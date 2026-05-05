package com.maomaocake.grafanaalloyplugin.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * Token-set helpers over [AlloyElementTypes]. The concrete token IElementTypes themselves live in
 * the GrammarKit-generated `AlloyElementTypes` interface — we reference them directly so the lexer
 * and parser agree on identity.
 */
object AlloyTokens {
    @JvmField val COMMENTS: TokenSet = TokenSet.create(
        AlloyElementTypes.LINE_COMMENT,
        AlloyElementTypes.BLOCK_COMMENT,
    )

    @JvmField val STRINGS: TokenSet = TokenSet.create(
        AlloyElementTypes.STRING,
        AlloyElementTypes.RAW_STRING,
    )

    /** Tokens that make the *following* newline act as a statement terminator (Go's `insertTerm`). */
    @JvmStatic
    fun endsStatement(type: IElementType): Boolean =
        type === AlloyElementTypes.IDENT ||
            type === AlloyElementTypes.STRING ||
            type === AlloyElementTypes.RAW_STRING ||
            type === AlloyElementTypes.NUMBER ||
            type === AlloyElementTypes.FLOAT ||
            type === AlloyElementTypes.BOOL ||
            type === AlloyElementTypes.NULL ||
            type === AlloyElementTypes.RCURLY ||
            type === AlloyElementTypes.RPAREN ||
            type === AlloyElementTypes.RBRACK
}
