package com.maomaocake.grafanaalloyplugin.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementFactory
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Makes [AlloyBlockLabel] behave as the declaring name for its enclosing block so find-usages
 * and rename treat it as a first-class declaration. The "name" is the unquoted contents of the
 * string label.
 *
 * The label is its own name-identifier (there's only one token inside — the STRING), which is
 * what [com.intellij.psi.PsiNameIdentifierOwner] requires for the platform's
 * find-rename-target-at-caret code path to surface it.
 */
abstract class AlloyBlockLabelMixin(node: ASTNode) : ASTWrapperPsiElement(node), AlloyBlockLabel {

    override fun getName(): String? = AlloyPsiUtil.unquoteLabel(this)

    override fun setName(name: String): PsiElement {
        val replacement = AlloyElementFactory.createBlockLabel(project, name)
        return replace(replacement)
    }

    // The STRING leaf is the "name identifier" from the platform's perspective: it's what
    // appears under the caret when the user invokes Rename, and TargetElementUtilBase walks
    // from that leaf back to this PsiNameIdentifierOwner.
    override fun getNameIdentifier(): PsiElement? = firstChild

    override fun getTextOffset(): Int {
        // Point the name-highlight at the contents rather than the opening quote so that
        // find-usages labels read like `rw_x` rather than `"rw_x`.
        return textRange.startOffset + 1
    }
}
