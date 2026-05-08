package com.maomaocake.grafanaalloyplugin.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Resolves a single-segment block name (e.g. the `add` in `add "default" {}`) to the matching
 * `declare "add" {}` block elsewhere in the file, when one exists.
 *
 * This is how Alloy's module system surfaces: `declare "name" {}` defines a module, and later
 * `name "instance" {}` invokes it. Treating the invocation's name as a reference to the declare
 * label lets Ctrl-click / Find Usages / Rename all work without a catalog of built-in
 * components interfering — we only create the reference when there's a matching declare.
 */
class AlloyDeclareReference(
    element: AlloyBlockName,
    rangeInElement: TextRange,
    private val target: AlloyBlockLabel,
) : PsiReferenceBase<AlloyBlockName>(element, rangeInElement, /* soft = */ true) {

    override fun resolve(): PsiElement = target
    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        /**
         * If [blockName] is a single-segment name and some `declare "X"` in the same file has
         * that name, return the range within [blockName] covering the identifier and the declare
         * label. Returns null for multi-segment names (`prometheus.scrape`) or when no matching
         * declare exists.
         */
        fun findTarget(blockName: AlloyBlockName): Pair<TextRange, AlloyBlockLabel>? {
            val idents = AlloyPsiUtil.blockNameIdents(blockName)
            if (idents.size != 1) return null
            val name = idents.first()

            // Skip if this *is* the `declare` keyword itself — its label is the target, not this.
            if (name == "declare") return null

            // Declare lookup uses the raw (unscoped) block index — a `foo "inst"` invocation
            // can reference a `declare "foo"` anywhere in the file/directory regardless of
            // its own surrounding scope. (The declare-scope filter in visibleBlocksFrom is
            // only about *component* references being confined to their module body; declare
            // invocation resolution is separate.)
            val file = blockName.containingFile ?: return null
            for (block in AlloyBlockIndex.visibleBlocks(file)) {
                if (block === blockName.parent) continue
                val nameIdents = AlloyPsiUtil.blockNameIdents(block.blockName)
                if (nameIdents.size != 1 || nameIdents[0] != "declare") continue
                val labelPsi = block.blockLabel ?: continue
                val labelText = AlloyPsiUtil.unquoteLabel(labelPsi) ?: continue
                if (labelText != name) continue

                val identNode = blockName.node.firstChildNode ?: continue
                val base = blockName.textRange.startOffset
                val range = TextRange(
                    identNode.textRange.startOffset - base,
                    identNode.textRange.endOffset - base,
                )
                return range to labelPsi
            }
            return null
        }
    }
}
