package com.maomaocake.grafanaalloyplugin.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Resolves an Alloy dotted reference (e.g. `prometheus.remote_write.rw_x.receiver`) to the
 * declaring block (e.g. `prometheus.remote_write "rw_x" { ... }`) when one exists in the same
 * file.
 *
 * Strategy: for each [AlloyBlock] in the file, form its *definition path* by concatenating the
 * block-name identifiers with the unquoted label. A reference resolves to that block when the
 * definition path is a strict prefix of the reference's identifier chain (strict, because there
 * must be at least one trailing segment — the export name — for the reference to be meaningful).
 *
 * This works without a component catalog, so multi-segment component names like
 * `prometheus.exporter.cadvisor` are handled automatically.
 */
class AlloyBlockReference(
    element: AlloyOperExpr,
    rangeInElement: TextRange,
    private val chainTexts: List<String>,
) : PsiReferenceBase<AlloyOperExpr>(element, rangeInElement, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile ?: return null
        for (block in PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)) {
            val label = block.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } ?: continue
            val defPath = AlloyPsiUtil.blockNameIdents(block.blockName) + label
            if (chainTexts.size > defPath.size &&
                chainTexts.subList(0, defPath.size) == defPath
            ) {
                return block.blockLabel
            }
        }
        return null
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
