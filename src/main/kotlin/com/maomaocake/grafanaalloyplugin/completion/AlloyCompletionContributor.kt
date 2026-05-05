package com.maomaocake.grafanaalloyplugin.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Document
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.maomaocake.grafanaalloyplugin.AlloyLanguage
import com.maomaocake.grafanaalloyplugin.catalog.AlloyBlock as CatalogBlock
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyComponent
import com.maomaocake.grafanaalloyplugin.psi.AlloyAttribute
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockBody
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyObjectExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Completion for Alloy.
 *
 *  - At **file top level**, offers every component in the catalog as a full `name "label" { }`
 *    template.
 *  - **Inside a block body** whose enclosing block name (or dotted ancestor chain) is in the
 *    catalog, offers only the args and nested blocks that component accepts — full catalog is
 *    suppressed, because Alloy doesn't nest `prometheus.*` inside `loki.write`.
 *  - Inside attribute values / object literals / block labels, offers nothing (the top-level
 *    popup shouldn't surface there).
 */
class AlloyCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(AlloyElementTypes.IDENT).withLanguage(AlloyLanguage),
            AlloyCompletionProvider(),
        )
    }
}

private class AlloyCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val site = classify(parameters) ?: return

        val typedPrefix = currentDottedPrefix(parameters)
        val scoped = if (typedPrefix.isEmpty()) result else result.withPrefixMatcher(typedPrefix)

        when (site) {
            is Site.TopLevel -> addTopLevelComponents(scoped)
            is Site.InsideBody -> addBlockMembers(scoped, site.component, site.path)
            is Site.Reference -> addReferenceCompletions(scoped, parameters, site.portTypeKey)
        }
    }

    private fun addTopLevelComponents(result: CompletionResultSet) {
        val catalog = AlloyCatalogService.getInstance().catalog
        for (component in catalog.components) {
            result.addElement(buildTopLevelLookup(component))
        }
    }

    /**
     * Completions inside a block body: offer the [component]'s args and nested blocks. [path]
     * points at which nested block body we're actually in (may be the component root, or
     * deeper like `prometheus.remote_write > endpoint > basic_auth`).
     */
    private fun addBlockMembers(result: CompletionResultSet, component: AlloyComponent, path: List<String>) {
        val (argsHere, blocksHere) = resolvePath(component, path) ?: return

        for (arg in argsHere) {
            result.addElement(
                LookupElementBuilder.create(arg.name)
                    .withPresentableText(arg.name)
                    .withTypeText(arg.goType, true)
                    .withTailText(if (arg.required) "  — required" else null, true)
                    .withInsertHandler(::insertAttributeTemplate)
            )
        }
        for (block in blocksHere) {
            val tail = buildString {
                append("  — block")
                if (block.repeated) append(" · repeatable")
                if (block.optional) append(" · optional")
            }
            result.addElement(
                LookupElementBuilder.create(block.name)
                    .withPresentableText(block.name)
                    .withTailText(tail, true)
                    .withTypeText(component.name, true)
                    .withInsertHandler { ctx, _ -> insertNestedBlockTemplate(ctx, block) }
            )
        }
    }

    /**
     * Completions inside an attribute value whose type is a port-typed list (e.g. `forward_to`).
     * Walks every labeled block in the file, looks up its declaring component in the catalog,
     * and for each export whose port type matches [portTypeKey] offers a reference like
     * `prometheus.remote_write.rw_x.receiver`.
     */
    private fun addReferenceCompletions(
        result: CompletionResultSet,
        parameters: CompletionParameters,
        portTypeKey: String,
    ) {
        val file = parameters.originalFile
        val catalog = AlloyCatalogService.getInstance().catalog
        for (block in PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)) {
            val label = block.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } ?: continue
            val nameIdents = AlloyPsiUtil.blockNameIdents(block.blockName)
            val declaredName = nameIdents.joinToString(".")
            val component = catalog.byName()[declaredName] ?: continue
            val matchingExports = component.exportsList().filter { ex ->
                // Some export types are the bare element type (e.g. `loki.LogsReceiver`) and
                // others the slice form (e.g. `[]discovery.Target`); strip an optional `[]`
                // prefix before normalizing so both shapes match correctly.
                normalizePortType(ex.goType.removePrefix("[]")) == portTypeKey
            }
            for (export in matchingExports) {
                val refText = "$declaredName.$label.${export.name}"
                result.addElement(
                    LookupElementBuilder.create(refText)
                        .withPresentableText(refText)
                        .withTypeText(shortPortTypeLabel(portTypeKey), true)
                        .withTailText("  — ${component.name}", true)
                )
            }
        }
    }
}

private fun shortPortTypeLabel(key: String): String = when (key) {
    "MetricsReceiver" -> "metrics"
    "LogsReceiver"    -> "logs"
    "ProfilesReceiver" -> "profiles"
    "OtelcolConsumer"  -> "otelcol"
    "Targets"          -> "targets"
    else               -> key
}

// -----------------------------------------------------------------------------
// Position classification
// -----------------------------------------------------------------------------

private sealed class Site {
    data object TopLevel : Site()
    data class InsideBody(val component: AlloyComponent, val path: List<String>) : Site()
    /**
     * The caret is inside an attribute value whose Go type is `[]X` where X is a port-typed
     * export (e.g. `forward_to = [ <caret> ]`). [portTypeKey] is the normalized element type
     * we match exports against (see [normalizePortType]).
     */
    data class Reference(val portTypeKey: String) : Site()
}

/**
 * Figures out what kind of completion to offer at the caret. Uses offset-based lookups against
 * the original (pre-dummy-identifier) file so we aren't fooled by the completion framework's
 * injection of `IntellijIdeaRulezzz`, which can land in odd places when the surrounding text
 * doesn't parse cleanly.
 *
 * Returns null if the caret is in a forbidden context (attribute value, object literal, block
 * label) — we want the completion list to be empty there, not fall back to top-level.
 */
/**
 * Figures out what kind of completion to offer at the caret.
 *
 * When the user is mid-typing (e.g. `prom<caret>` inside a block body), the parser's error
 * recovery wraps the in-progress text in generic `DummyBlock` nodes and the PSI around the
 * caret no longer has a clean [AlloyBlockBody] / [AlloyBlock] ancestor chain. To avoid that,
 * we classify against the **original** file (the pre-dummy-injection version), which parses
 * cleanly because the in-progress identifier isn't part of it. Offset-based lookup at
 * `caretOffset - 1` finds the enclosing structure there.
 *
 * Returns null if the caret is in a forbidden context (attribute value, object literal,
 * block label) — we want the completion list to be empty there, not fall back to top-level.
 */
/**
 * Figures out what kind of completion to offer at the caret.
 *
 * Implementation note: when the user is mid-typing, the partial identifier causes the PSI
 * around the caret to be wrapped in generic `DummyBlock` error-recovery nodes, so structural
 * PSI walks from the caret don't reliably find the enclosing [AlloyBlockBody]. Instead we
 * scan the text for enclosing unbalanced `{` characters (ignoring ones inside strings /
 * comments by using PSI to tell us which offsets to skip), then read the block-name-ish text
 * immediately before each `{`.
 *
 * This is not as nice as "just walk the PSI tree" but it's robust against error recovery and
 * we validate the block name against the catalog before acting on it.
 *
 * Returns null if the caret is in a forbidden context (attribute value, object literal,
 * block label) — we want the completion list to be empty there, not fall back to top-level.
 */
private fun classify(parameters: CompletionParameters): Site? {
    val original = parameters.originalFile
    val caret = parameters.offset
    val pivot = (caret - 1).coerceAtLeast(0)

    // Reject block-label and object-literal contexts outright.
    PsiTreeUtil.findElementOfClassAtOffset(original, pivot, AlloyBlockLabel::class.java, false)?.let { return null }
    PsiTreeUtil.findElementOfClassAtOffset(original, pivot, AlloyObjectExpr::class.java, false)?.let { return null }

    // Build the chain of enclosing blocks by scanning the text backwards for unbalanced `{`.
    val text = original.text
    val rawChain = collectEnclosingChainFromText(text, caret)

    // `declare "name" { ... }` defines a module whose body is a top-level scope — skip it so
    // the chain reflects what's happening within the module.
    val chain = if (rawChain.firstOrNull() == "declare") rawChain.drop(1) else rawChain

    // Attribute context: if the caret is inside an attribute on the RHS, check whether the
    // attribute's declared type is a port-typed list (e.g. `forward_to`). When it is, offer
    // reference completions filtered by port type; otherwise bail (an arbitrary string or
    // number RHS has no useful completions to offer).
    val attribute = PsiTreeUtil.findElementOfClassAtOffset(original, pivot, AlloyAttribute::class.java, false)
    if (attribute != null) {
        val attrName = attribute.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT }?.text
            ?: return null
        val rootName = chain.firstOrNull() ?: return null
        val component = AlloyCatalogService.getInstance().catalog.byName()[rootName] ?: return null
        val (argsHere, _) = resolvePath(component, chain.drop(1)) ?: return null
        val arg = argsHere.firstOrNull { it.name == attrName } ?: return null
        val elementType = arg.goType.removePrefix("[]").takeIf { it != arg.goType } ?: return null
        val portKey = normalizePortType(elementType) ?: return null
        return Site.Reference(portKey)
    }

    val rootName = chain.firstOrNull() ?: return Site.TopLevel
    val catalog = AlloyCatalogService.getInstance().catalog
    val component = catalog.byName()[rootName] ?: return null
    return Site.InsideBody(component = component, path = chain.drop(1))
}

/**
 * Canonical key for a port type — normalizes different spellings used in the catalog's
 * `goType` column to the same key. Exports expose the bare type (`loki.LogsReceiver`);
 * arg-list element types match after stripping `[]`.
 */
internal fun normalizePortType(goType: String): String? = when {
    goType == "loki.LogsReceiver"       -> "LogsReceiver"
    goType == "storage.Appendable"      -> "MetricsReceiver"
    goType == "pyroscope.Appendable"    -> "ProfilesReceiver"
    goType == "otelcol.Consumer"        -> "OtelcolConsumer"
    goType == "discovery.Target"        -> "Targets"
    else                                -> null
}

/**
 * Walks [component]'s nested blocks along [path] and returns the (args, blocks) available at
 * that depth. Returns null if the path doesn't resolve.
 */
private fun resolvePath(
    component: AlloyComponent,
    path: List<String>,
): Pair<List<com.maomaocake.grafanaalloyplugin.catalog.AlloyArg>, List<CatalogBlock>>? {
    if (path.isEmpty()) return component.argsList() to component.blocksList()
    var blocks = component.blocksList()
    var argsHere: List<com.maomaocake.grafanaalloyplugin.catalog.AlloyArg> = emptyList()
    for (segment in path) {
        val match = blocks.firstOrNull { it.name == segment } ?: return null
        argsHere = match.argsList()
        blocks = match.blocksList()
    }
    return argsHere to blocks
}

/**
 * Scans [text] backwards from [caret] looking for the block names that enclose this caret.
 * Returns outermost-first, e.g. `["loki.write", "endpoint", "basic_auth"]` for a caret inside
 * `loki.write "x" { endpoint { basic_auth { <caret> } } }`.
 *
 * Ignores `{` / `}` characters that appear inside double-quoted or backtick-quoted strings,
 * and inside `//` line comments and `/* ... */` block comments. Uses a small scanner that
 * walks the text once — robust against error-recovery PSI.
 */
private fun collectEnclosingChainFromText(text: CharSequence, caret: Int): List<String> {
    val openingBraceOffsets = mutableListOf<Int>()
    val stack = ArrayDeque<Int>()
    var i = 0
    val end = caret.coerceAtMost(text.length)
    while (i < end) {
        val c = text[i]
        when {
            c == '/' && i + 1 < end && text[i + 1] == '/' -> {
                // line comment: skip to \n
                i += 2
                while (i < end && text[i] != '\n') i++
            }
            c == '/' && i + 1 < end && text[i + 1] == '*' -> {
                i += 2
                while (i + 1 < end && !(text[i] == '*' && text[i + 1] == '/')) i++
                if (i + 1 < end) i += 2 else i = end
            }
            c == '"' -> {
                i++
                while (i < end && text[i] != '"') {
                    if (text[i] == '\\' && i + 1 < end) i += 2 else i++
                }
                if (i < end) i++
            }
            c == '`' -> {
                i++
                while (i < end && text[i] != '`') i++
                if (i < end) i++
            }
            c == '{' -> {
                stack.addLast(i)
                i++
            }
            c == '}' -> {
                stack.removeLastOrNull()
                i++
            }
            else -> i++
        }
    }
    openingBraceOffsets.addAll(stack)

    val chain = mutableListOf<String>()
    for (braceOffset in openingBraceOffsets) {
        // Walk backward from the `{` to extract the dotted block name. Skip whitespace and an
        // optional `"..."` label.
        var j = braceOffset - 1
        while (j >= 0 && text[j].isWhitespace()) j--
        // Skip optional quoted label.
        if (j >= 0 && text[j] == '"') {
            j--
            while (j >= 0 && text[j] != '"') {
                if (text[j] == '\\' && j >= 1) j -= 2 else j--
            }
            if (j >= 0) j-- // step past opening quote
            while (j >= 0 && text[j].isWhitespace()) j--
        }
        // Now read the dotted identifier.
        val nameEnd = j + 1
        while (j >= 0 && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '.')) j--
        val name = text.subSequence(j + 1, nameEnd).toString()
        if (name.isNotEmpty()) chain += name
    }
    return chain
}


// -----------------------------------------------------------------------------
// Top-level component lookups
// -----------------------------------------------------------------------------

private fun buildTopLevelLookup(component: AlloyComponent): LookupElement {
    val tail = buildString {
        append("  — ")
        append(component.stability)
        if (component.community) append(" · community")
        component.exported().firstOrNull()?.let { append(" · ").append(portTypeShort(it)) }
    }
    return LookupElementBuilder.create(component.name)
        .withPresentableText(component.name)
        .withTailText(tail, /* grayed = */ true)
        .withTypeText(component.namespace)
        .withInsertHandler(::insertComponentTemplate)
        .withLookupStrings(lookupStrings(component.name))
}

private fun lookupStrings(name: String): Set<String> {
    val parts = name.split('.')
    val set = linkedSetOf(name)
    set += parts
    for (i in parts.indices) set += parts.subList(i, parts.size).joinToString(".")
    return set
}

private fun portTypeShort(t: String): String = when {
    t.contains("MetricsReceiver") -> "metrics"
    t.contains("LogsReceiver")    -> "logs"
    t.contains("otelcol")         -> "otelcol"
    t.contains("Pyroscope")       -> "profiles"
    t.contains("Targets")         -> "targets"
    else                          -> t
}

// -----------------------------------------------------------------------------
// Insertion handlers
// -----------------------------------------------------------------------------

/** Top-level `prometheus.scrape "name" { <caret> }` template. */
private fun insertComponentTemplate(context: InsertionContext, item: LookupElement) {
    val document = context.document
    val tailOffset = context.tailOffset
    val componentName = item.lookupString
    val startOffset = dottedPrefixStart(document, context.startOffset)

    val afterCaret = document.charsSequence.subSequence(
        tailOffset,
        minOf(tailOffset + 32, document.textLength),
    ).toString()
    if (afterCaret.trimStart().startsWith("\"") || afterCaret.trimStart().startsWith("{")) {
        document.replaceString(startOffset, tailOffset, componentName)
        context.editor.caretModel.moveToOffset(startOffset + componentName.length)
        return
    }

    val template = "$componentName \"name\" {\n    \n}"
    document.replaceString(startOffset, tailOffset, template)
    val labelStart = startOffset + componentName.length + 2 // + ` "`
    val labelEnd = labelStart + "name".length
    context.editor.caretModel.moveToOffset(labelStart)
    context.editor.selectionModel.setSelection(labelStart, labelEnd)
    AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
}

/** `attr_name = <caret>` template. */
private fun insertAttributeTemplate(context: InsertionContext, item: LookupElement) {
    val document = context.document
    val tailOffset = context.tailOffset
    val name = item.lookupString
    val startOffset = dottedPrefixStart(document, context.startOffset)

    // If there's already ` = value` following, don't double-insert.
    val afterCaret = document.charsSequence.subSequence(
        tailOffset,
        minOf(tailOffset + 8, document.textLength),
    ).toString()
    if (afterCaret.trimStart().startsWith("=")) {
        document.replaceString(startOffset, tailOffset, name)
        context.editor.caretModel.moveToOffset(startOffset + name.length)
        return
    }
    document.replaceString(startOffset, tailOffset, "$name = ")
    context.editor.caretModel.moveToOffset(startOffset + name.length + 3)
    AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
}

/** Nested block — `endpoint { <caret> }` or `endpoint "label" { <caret> }` when labeled. */
private fun insertNestedBlockTemplate(context: InsertionContext, block: CatalogBlock) {
    val document = context.document
    val tailOffset = context.tailOffset
    val startOffset = dottedPrefixStart(document, context.startOffset)
    val needsLabel = block.label
    val header = if (needsLabel) "${block.name} \"name\" {\n    \n}" else "${block.name} {\n    \n}"
    document.replaceString(startOffset, tailOffset, header)
    if (needsLabel) {
        val labelStart = startOffset + block.name.length + 2
        val labelEnd = labelStart + "name".length
        context.editor.caretModel.moveToOffset(labelStart)
        context.editor.selectionModel.setSelection(labelStart, labelEnd)
    } else {
        val bodyCaret = startOffset + block.name.length + 3 // "  { "
        context.editor.caretModel.moveToOffset(bodyCaret + 1 + 4)
    }
    AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
}

// -----------------------------------------------------------------------------
// Shared helpers
// -----------------------------------------------------------------------------

/**
 * Returns the dotted prefix at the completion offset (reading the document, not the PSI, so
 * we don't trip over `IntellijIdeaRulezzz`). Used to scope the completion matcher so
 * `prometheus.ex` only matches `prometheus.ex*`.
 */
private fun currentDottedPrefix(parameters: CompletionParameters): String {
    val text = parameters.editor.document.charsSequence
    var start = parameters.offset
    while (start > 0) {
        val c = text[start - 1]
        if (c.isLetterOrDigit() || c == '_' || c == '.') start-- else break
    }
    return text.subSequence(start, parameters.offset).toString()
}

private fun dottedPrefixStart(document: Document, identStart: Int): Int {
    val text = document.charsSequence
    var start = identStart
    while (start > 0) {
        val c = text[start - 1]
        if (c.isLetterOrDigit() || c == '_' || c == '.') start-- else break
    }
    return start
}
