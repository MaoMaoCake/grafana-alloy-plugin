package com.maomaocake.grafanaalloyplugin.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.references.AlloyDeclareReference

/**
 * Exposes a reference on a single-segment block name when it invokes a local `declare "X" {}`
 * module (e.g. `add "default" { ... }` → `declare "add" { ... }`).
 */
abstract class AlloyBlockNameMixin(node: ASTNode) : ASTWrapperPsiElement(node), AlloyBlockName {

    override fun getReferences(): Array<PsiReference> {
        val (range, target) = AlloyDeclareReference.findTarget(this) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(AlloyDeclareReference(this, range, target))
    }
}
