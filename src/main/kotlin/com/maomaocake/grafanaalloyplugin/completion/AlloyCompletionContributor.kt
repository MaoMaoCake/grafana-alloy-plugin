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
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyComponent
import com.maomaocake.grafanaalloyplugin.psi.AlloyAttribute
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockBody
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyObjectExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyStatement

/**
 * Completion for Alloy component declarations.
 *
 * Fires on any `IDENT` (or dotted block-name) leaf at a position where a block can start:
 * the file top level, or directly inside a parent block body. Skips positions inside
 * expressions / attributes / object literals / labels.
 */
class AlloyCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(AlloyElementTypes.IDENT).withLanguage(AlloyLanguage),
            ComponentNameCompletionProvider(),
        )
    }
}

private class ComponentNameCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (!isBlockStartPosition(parameters.position)) return

        // The default prefix is just the current IDENT leaf (e.g. `ex` of `prometheus.ex`).
        // Extend it backwards across dotted segments so the matcher treats
        // `prometheus.ex` as a single prefix and only surfaces components whose name
        // *starts* with that string, rather than CamelHump-matching `ex` against every
        // component.
        val typedPrefix = currentDottedPrefix(parameters)
        val scoped = if (typedPrefix.isEmpty()) result else result.withPrefixMatcher(typedPrefix)

        val catalog = AlloyCatalogService.getInstance().catalog
        for (component in catalog.components) {
            scoped.addElement(buildLookup(component))
        }
    }

    /**
     * Returns the full dotted text ending at the completion offset — e.g. `prometheus.ex`.
     * Reads the document directly rather than trusting the PSI, because at completion time
     * the platform has injected a `IntellijIdeaRulezzz` dummy identifier into the tree.
     */
    private fun currentDottedPrefix(parameters: CompletionParameters): String {
        val text = parameters.editor.document.charsSequence
        var start = parameters.offset
        while (start > 0) {
            val c = text[start - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '.') {
                start--
            } else {
                break
            }
        }
        return text.subSequence(start, parameters.offset).toString()
    }

    private fun buildLookup(component: AlloyComponent): LookupElement {
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
            .withInsertHandler(::insertBlockTemplate)
            .withLookupStrings(lookupStrings(component.name))
    }
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

/**
 * After the user picks a completion, expand `prometheus.scrape` into a full block template:
 *
 *     prometheus.scrape "name" {
 *
 *     }
 *
 * The `name` placeholder is pre-selected so the user can type over it.
 */
private fun insertBlockTemplate(context: InsertionContext, item: LookupElement) {
    val document: Document = context.document
    val tailOffset = context.tailOffset
    val componentName = item.lookupString

    // Replace the *entire dotted prefix* the user has typed (e.g. `prometheus.ex`), not just
    // the trailing IDENT — otherwise we'd duplicate the namespace.
    val startOffset = dottedPrefixStart(document, context.startOffset)

    // If the user already has trailing text (e.g. partial ` "foo"` or a block body), leave it
    // alone and just replace the identifier range with the component name.
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

private fun dottedPrefixStart(document: Document, identStart: Int): Int {
    val text = document.charsSequence
    var start = identStart
    while (start > 0) {
        val c = text[start - 1]
        if (c.isLetterOrDigit() || c == '_' || c == '.') start-- else break
    }
    return start
}

/**
 * True when [position] (an IDENT leaf) sits at a place where a block can start: at the
 * top of the file, or directly inside a block body, *not* inside an attribute value, object
 * literal, or block label.
 *
 * Walks upward from the leaf, returning false as soon as we hit a context that forbids a
 * block-start (attribute value, object literal, block label). Returns true once we reach an
 * `AlloyFile` or `AlloyBlockBody` without hitting a forbidden context.
 */
private fun isBlockStartPosition(position: PsiElement): Boolean {
    var cur: PsiElement? = position
    while (cur != null) {
        when (cur) {
            is AlloyBlockLabel -> return false
            is AlloyObjectExpr -> return false
            is AlloyAttribute  -> {
                // If our leaf is (still) the leading IDENT of the attribute — i.e. the user has
                // typed the attribute *key* — we don't want to offer component completions
                // there either. Either way, inside an attribute = not a block-start.
                return false
            }
            is AlloyBlockBody  -> return true
            is AlloyFile       -> return true
        }
        cur = cur.parent
    }
    return false
}
