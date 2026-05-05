package com.maomaocake.grafanaalloyplugin.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

object AlloyPsiUtil {

    /** Identifier texts of a dotted block name in document order. */
    fun blockNameIdents(blockName: AlloyBlockName): List<String> =
        blockName.node.getChildren(TokenSet.create(AlloyElementTypes.IDENT))
            .map { it.text }

    /**
     * Strips the surrounding double quotes from a block label. Does not expand escapes — the
     * contents are required to be a valid identifier (per upstream [syntax/parser/internal.go]),
     * so backslash escapes won't appear in practice.
     */
    fun unquoteLabel(label: AlloyBlockLabel): String? {
        val text = label.text
        if (text.length < 2 || text[0] != '"' || text[text.length - 1] != '"') return null
        return text.substring(1, text.length - 1)
    }

    /**
     * If [oper] is a pure dotted identifier chain (`a.b.c.d`) — no function calls, no indexing —
     * return the IDENT [PsiElement]s in document order. Otherwise return null.
     */
    fun identChain(oper: AlloyOperExpr): List<PsiElement>? {
        if (oper.callExprList.isNotEmpty() || oper.indexExprList.isNotEmpty()) return null

        val idExpr = oper.primaryExpr.firstChild as? AlloyIdentifierExpr ?: return null
        val headIdent = idExpr.firstChild
            ?.takeIf { it.node.elementType == AlloyElementTypes.IDENT }
            ?: return null

        val chain = mutableListOf(headIdent)
        for (access in oper.accessExprList) {
            // AccessExpr ::= DOT IDENT — tail IDENT is the access name.
            val accessIdent = access.lastChild
                ?.takeIf { it.node.elementType == AlloyElementTypes.IDENT }
                ?: return null
            chain += accessIdent
        }
        return chain
    }
}
