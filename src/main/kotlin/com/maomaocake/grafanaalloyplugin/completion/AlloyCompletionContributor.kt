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
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.ProcessingContext
import com.maomaocake.grafanaalloyplugin.AlloyLanguage
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyComponent
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockBody
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile

/**
 * Completion for Alloy component declarations.
 *
 * Fires when the caret is on an [AlloyElementTypes.IDENT] leaf whose parent chain reaches a
 * top-level-statement context — either directly under the [AlloyFile] or inside an
 * [AlloyBlockBody]. In practice this is *any* position where the user is starting a block
 * name, which is exactly when we want to surface component suggestions.
 */
class AlloyCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            componentNamePattern(),
            ComponentNameCompletionProvider(),
        )
    }

    private fun componentNamePattern(): PsiElementPattern.Capture<PsiElement> =
        PlatformPatterns.psiElement(AlloyElementTypes.IDENT)
            .withLanguage(AlloyLanguage)
}

private class ComponentNameCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (!isComponentNamePosition(parameters.position)) return

        val catalog = AlloyCatalogService.getInstance().catalog
        for (component in catalog.components) {
            result.addElement(buildLookup(component))
        }
    }

    private fun buildLookup(component: AlloyComponent): LookupElement {
        val stability = component.stability
        val tail = buildString {
            append("  — ")
            append(stability)
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
    // Make completion discoverable by namespace or leaf: typing `scrape` should find
    // `prometheus.scrape`, typing `prom` should find all prometheus.*.
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
 *         <caret>
 *     }
 *
 * The caret is placed inside the body so the user can start typing attributes.
 */
private fun insertBlockTemplate(context: InsertionContext, item: LookupElement) {
    val document: Document = context.document
    val startOffset = context.startOffset
    val tailOffset = context.tailOffset
    val componentName = item.lookupString

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
    // Select the placeholder label `name` so the user can type over it.
    val labelStart = startOffset + componentName.length + 2 // + ` "`
    val labelEnd = labelStart + "name".length
    context.editor.caretModel.moveToOffset(labelStart)
    context.editor.selectionModel.setSelection(labelStart, labelEnd)

    AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
}

/**
 * True when [position] (an IDENT leaf) is in a place where a *block* can start: at the file
 * top level or directly inside another block's body. Excludes positions inside expressions,
 * attribute values, object literals, etc.
 */
private fun isComponentNamePosition(position: PsiElement): Boolean {
    // Walk up past the IDENT leaf's immediate wrappers (BlockName, IdentifierExpr, etc.) to
    // find what kind of statement we're in.
    var cur: PsiElement? = position.parent
    // An IDENT at a statement-start parses, while the user is still typing, as either a
    // block_name (when nothing follows) or an identifier_expr (when the parser guessed a
    // reference). Either way, the grandparent will be a Statement whose parent is a file or
    // block body.
    while (cur != null) {
        val parent = cur.parent ?: return false
        if (parent is AlloyFile || parent is AlloyBlockBody) return true
        // Whitespace nodes between statements don't count as a wrapping expression.
        if (parent is PsiWhiteSpace) {
            cur = parent
            continue
        }
        // If we hit an AlloyStatement we're in good shape regardless of what's above it —
        // the user is at a statement boundary.
        if (parent::class.java.simpleName == "AlloyStatementImpl") return true
        // Any other parent (expression, array, object literal, attribute) means we're NOT
        // at a block-start.
        if (parent::class.java.simpleName != "AlloyBlockNameImpl" &&
            parent::class.java.simpleName != "AlloyIdentifierExprImpl" &&
            parent::class.java.simpleName != "AlloyPrimaryExprImpl" &&
            parent::class.java.simpleName != "AlloyOperExprImpl" &&
            parent::class.java.simpleName != "AlloyUnaryExprImpl" &&
            parent::class.java.simpleName != "AlloyPowExprImpl" &&
            parent::class.java.simpleName != "AlloyMulExprImpl" &&
            parent::class.java.simpleName != "AlloyAddExprImpl" &&
            parent::class.java.simpleName != "AlloyCmpExprImpl" &&
            parent::class.java.simpleName != "AlloyAndExprImpl" &&
            parent::class.java.simpleName != "AlloyOrExprImpl" &&
            parent::class.java.simpleName != "AlloyExpressionImpl"
        ) {
            return false
        }
        cur = parent
    }
    return false
}
