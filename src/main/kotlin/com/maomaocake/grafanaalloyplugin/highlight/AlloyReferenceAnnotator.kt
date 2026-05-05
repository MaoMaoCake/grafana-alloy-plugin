package com.maomaocake.grafanaalloyplugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Reports reference-level issues in Alloy files:
 *  - Duplicate labels: two blocks declared with the same dotted name + label.
 *  - Unresolved component references: a chain like `prometheus.scrape.name.targets` whose first
 *    segment is a known Alloy namespace but whose `(name, label)` prefix matches no block in
 *    this file.
 */
class AlloyReferenceAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is AlloyFile    -> checkDuplicateLabels(element, holder)
            is AlloyOperExpr -> checkUnresolvedReference(element, holder)
            else             -> Unit
        }
    }

    private fun checkDuplicateLabels(file: AlloyFile, holder: AnnotationHolder) {
        val byKey = mutableMapOf<String, MutableList<AlloyBlock>>()
        for (block in PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)) {
            val labelPsi = block.blockLabel ?: continue
            val label = AlloyPsiUtil.unquoteLabel(labelPsi) ?: continue
            val key = (AlloyPsiUtil.blockNameIdents(block.blockName) + label).joinToString(".")
            byKey.getOrPut(key) { mutableListOf() } += block
        }
        for ((key, blocks) in byKey) {
            if (blocks.size < 2) continue
            for (block in blocks) {
                val labelPsi = block.blockLabel ?: continue
                holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate component label: `$key`")
                    .range(labelPsi.textRange)
                    .create()
            }
        }
    }

    private fun checkUnresolvedReference(oper: AlloyOperExpr, holder: AnnotationHolder) {
        val chain = AlloyPsiUtil.identChain(oper) ?: return
        if (chain.size < 3) return
        val firstSegment = chain.first().text
        // Only flag chains whose first segment is a known Alloy namespace. Anything else may be
        // a local (module argument, declare-block input, etc.) that we can't model yet, and
        // flagging those would produce a wave of false positives.
        AlloyColors.namespaceKey(firstSegment) ?: return

        // References resolve eagerly in the mixin; if getReferences() is empty, there's no match.
        if (oper.references.any { it.resolve() != null }) return

        val chainStart = chain.first().textRange.startOffset
        val chainEnd = chain.last().textRange.endOffset
        holder.newAnnotation(
            HighlightSeverity.WARNING,
            "Unresolved Alloy component reference: `${chain.joinToString(".") { it.text }}`",
        )
            .range(com.intellij.openapi.util.TextRange(chainStart, chainEnd))
            .create()
    }
}
