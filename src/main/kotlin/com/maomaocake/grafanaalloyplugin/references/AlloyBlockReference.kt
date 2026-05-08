package com.maomaocake.grafanaalloyplugin.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Resolves the label segment of an Alloy dotted reference (e.g. the `rw_x` in
 * `prometheus.remote_write.rw_x.receiver`) to the declaring [AlloyBlockLabel].
 *
 * The reference range is narrowed to the label segment so that:
 *  - Find Usages on the block label surfaces only the label sites, not the whole chain.
 *  - Rename rewrites only the label identifier (via the registered
 *    [com.maomaocake.grafanaalloyplugin.psi.AlloyOperExprManipulator]).
 */
class AlloyBlockReference(
    element: AlloyOperExpr,
    rangeInElement: TextRange,
    private val target: AlloyBlockLabel,
) : PsiReferenceBase<AlloyOperExpr>(element, rangeInElement, /* soft = */ true) {

    override fun resolve(): PsiElement = target

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        /**
         * If [oper] is a dotted identifier chain of length ≥ 3 whose prefix matches some block's
         * `(blockName.idents + label)`, return the range within [oper] covering that label
         * segment together with the declaring [AlloyBlockLabel]. Otherwise null.
         *
         * Block names vary in length (e.g. `prometheus.scrape` vs `prometheus.exporter.cadvisor`),
         * so the label position isn't fixed — we find it by walking the file's declarations.
         */
        fun findTarget(oper: AlloyOperExpr): Pair<TextRange, AlloyBlockLabel>? {
            val chain = AlloyPsiUtil.identChain(oper) ?: return null
            if (chain.size < 3) return null
            val chainTexts = chain.map { it.text }

            for (block in AlloyBlockIndex.visibleBlocksFrom(oper)) {
                val labelPsi = block.blockLabel ?: continue
                val labelText = AlloyPsiUtil.unquoteLabel(labelPsi) ?: continue
                val nameIdents = AlloyPsiUtil.blockNameIdents(block.blockName)
                if (chainTexts.size <= nameIdents.size) continue
                if (chainTexts.subList(0, nameIdents.size) != nameIdents) continue
                if (chainTexts[nameIdents.size] != labelText) continue

                val labelIdent = chain[nameIdents.size]
                val base = oper.textRange.startOffset
                val range = TextRange(
                    labelIdent.textRange.startOffset - base,
                    labelIdent.textRange.endOffset - base,
                )
                return range to labelPsi
            }
            return null
        }
    }
}
