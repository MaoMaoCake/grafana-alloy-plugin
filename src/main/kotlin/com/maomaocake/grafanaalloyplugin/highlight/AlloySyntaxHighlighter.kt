package com.maomaocake.grafanaalloyplugin.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.maomaocake.grafanaalloyplugin.lexer.AlloyLexerAdapter
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes

object AlloyColors {
    val IDENTIFIER     = createTextAttributesKey("ALLOY_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val KEYWORD        = createTextAttributesKey("ALLOY_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val STRING         = createTextAttributesKey("ALLOY_STRING", DefaultLanguageHighlighterColors.STRING)
    val NUMBER         = createTextAttributesKey("ALLOY_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val OPERATOR       = createTextAttributesKey("ALLOY_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val PARENS         = createTextAttributesKey("ALLOY_PARENS", DefaultLanguageHighlighterColors.PARENTHESES)
    val BRACKETS       = createTextAttributesKey("ALLOY_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    val BRACES         = createTextAttributesKey("ALLOY_BRACES", DefaultLanguageHighlighterColors.BRACES)
    val COMMA          = createTextAttributesKey("ALLOY_COMMA", DefaultLanguageHighlighterColors.COMMA)
    val DOT            = createTextAttributesKey("ALLOY_DOT", DefaultLanguageHighlighterColors.DOT)
    val LINE_COMMENT   = createTextAttributesKey("ALLOY_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val BLOCK_COMMENT  = createTextAttributesKey("ALLOY_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val BAD_CHARACTER  = createTextAttributesKey("ALLOY_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    // Per-namespace colors for component block names. Default values are shipped in
    // `resources/colorSchemes/Alloy{Default,Darcula}.xml` and picked up via the
    // `additionalTextAttributes` extension point in plugin.xml.
    val NAMESPACE_PROMETHEUS             = createTextAttributesKey("ALLOY_NAMESPACE_PROMETHEUS")
    val NAMESPACE_LOKI                   = createTextAttributesKey("ALLOY_NAMESPACE_LOKI")
    val NAMESPACE_MIMIR                  = createTextAttributesKey("ALLOY_NAMESPACE_MIMIR")
    val NAMESPACE_OTELCOL                = createTextAttributesKey("ALLOY_NAMESPACE_OTELCOL")
    val NAMESPACE_PYROSCOPE              = createTextAttributesKey("ALLOY_NAMESPACE_PYROSCOPE")
    val NAMESPACE_DISCOVERY              = createTextAttributesKey("ALLOY_NAMESPACE_DISCOVERY")
    val NAMESPACE_FARO                   = createTextAttributesKey("ALLOY_NAMESPACE_FARO")
    val NAMESPACE_BEYLA                  = createTextAttributesKey("ALLOY_NAMESPACE_BEYLA")
    val NAMESPACE_LOCAL                  = createTextAttributesKey("ALLOY_NAMESPACE_LOCAL")
    val NAMESPACE_REMOTE                 = createTextAttributesKey("ALLOY_NAMESPACE_REMOTE")
    val NAMESPACE_DATABASE_OBSERVABILITY = createTextAttributesKey("ALLOY_NAMESPACE_DATABASE_OBSERVABILITY")

    fun namespaceKey(namespace: String): TextAttributesKey? = when (namespace) {
        "prometheus"             -> NAMESPACE_PROMETHEUS
        "loki"                   -> NAMESPACE_LOKI
        "mimir"                  -> NAMESPACE_MIMIR
        "otelcol"                -> NAMESPACE_OTELCOL
        "pyroscope"              -> NAMESPACE_PYROSCOPE
        "discovery"              -> NAMESPACE_DISCOVERY
        "faro"                   -> NAMESPACE_FARO
        "beyla"                  -> NAMESPACE_BEYLA
        "local"                  -> NAMESPACE_LOCAL
        "remote"                 -> NAMESPACE_REMOTE
        "database_observability" -> NAMESPACE_DATABASE_OBSERVABILITY
        else                     -> null
    }
}

class AlloySyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = AlloyLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {
            AlloyElementTypes.BOOL,
            AlloyElementTypes.NULL               -> pack(AlloyColors.KEYWORD)

            AlloyElementTypes.IDENT              -> pack(AlloyColors.IDENTIFIER)

            AlloyElementTypes.STRING,
            AlloyElementTypes.RAW_STRING         -> pack(AlloyColors.STRING)

            AlloyElementTypes.NUMBER,
            AlloyElementTypes.FLOAT              -> pack(AlloyColors.NUMBER)

            AlloyElementTypes.ASSIGN,
            AlloyElementTypes.OR,
            AlloyElementTypes.AND,
            AlloyElementTypes.NOT,
            AlloyElementTypes.EQ,
            AlloyElementTypes.NEQ,
            AlloyElementTypes.LT, AlloyElementTypes.LTE,
            AlloyElementTypes.GT, AlloyElementTypes.GTE,
            AlloyElementTypes.PLUS, AlloyElementTypes.MINUS,
            AlloyElementTypes.STAR, AlloyElementTypes.SLASH,
            AlloyElementTypes.PERCENT, AlloyElementTypes.CARET
                                                 -> pack(AlloyColors.OPERATOR)

            AlloyElementTypes.LPAREN,
            AlloyElementTypes.RPAREN             -> pack(AlloyColors.PARENS)

            AlloyElementTypes.LBRACK,
            AlloyElementTypes.RBRACK             -> pack(AlloyColors.BRACKETS)

            AlloyElementTypes.LCURLY,
            AlloyElementTypes.RCURLY             -> pack(AlloyColors.BRACES)

            AlloyElementTypes.COMMA              -> pack(AlloyColors.COMMA)
            AlloyElementTypes.DOT                -> pack(AlloyColors.DOT)

            AlloyElementTypes.LINE_COMMENT       -> pack(AlloyColors.LINE_COMMENT)
            AlloyElementTypes.BLOCK_COMMENT      -> pack(AlloyColors.BLOCK_COMMENT)

            TokenType.BAD_CHARACTER              -> pack(AlloyColors.BAD_CHARACTER)

            else                                 -> emptyArray()
        }
}
