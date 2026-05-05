package com.maomaocake.grafanaalloyplugin.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.references.AlloyBlockReference

/**
 * Base implementation for `AlloyOperExpr` (a dotted primary with `.ident` / `[expr]` / `(args)`
 * chains). Exposes an [AlloyBlockReference] on the label segment whenever the chain matches some
 * block's `(name + label)` prefix — resolution happens eagerly so we can narrow the reference
 * range to just the label identifier.
 */
abstract class AlloyOperExprMixin(node: ASTNode) : ASTWrapperPsiElement(node), AlloyOperExpr {

    override fun getReferences(): Array<PsiReference> {
        val (range, target) = AlloyBlockReference.findTarget(this) ?: return PsiReference.EMPTY_ARRAY
        return arrayOf(AlloyBlockReference(this, range, target))
    }
}
