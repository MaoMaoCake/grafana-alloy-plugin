package com.maomaocake.grafanaalloyplugin.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil
import com.maomaocake.grafanaalloyplugin.references.AlloyBlockReference

/**
 * Base implementation for `AlloyOperExpr` (a dotted primary with `.ident` / `[expr]` / `(args)`
 * chains). When the expression is a pure `a.b.c.d` identifier chain of length ≥ 3, it exposes an
 * [AlloyBlockReference] that resolves to the declaring block.
 */
abstract class AlloyOperExprMixin(node: ASTNode) : ASTWrapperPsiElement(node), AlloyOperExpr {

    override fun getReferences(): Array<PsiReference> {
        val self = this
        val chain = AlloyPsiUtil.identChain(self) ?: return PsiReference.EMPTY_ARRAY
        if (chain.size < 3) return PsiReference.EMPTY_ARRAY

        val base = self.textRange.startOffset
        val range = TextRange(
            chain.first().textRange.startOffset - base,
            chain.last().textRange.endOffset - base,
        )
        return arrayOf(AlloyBlockReference(self, range, chain.map { it.text }))
    }
}
