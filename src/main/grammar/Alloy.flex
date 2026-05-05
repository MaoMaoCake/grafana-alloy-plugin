package com.maomaocake.grafanaalloyplugin.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes;

%%

%class _AlloyLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
  // Mirrors Go's scanner.insertTerm (syntax/scanner/scanner.go).
  // A newline emits TERMINATOR only when the previous real token was one that
  // can end a statement (IDENT, STRING, NUMBER/FLOAT, BOOL, NULL, }, ), ]).
  private boolean insertTerm = false;

  private IElementType term(IElementType t, boolean ends) {
    insertTerm = ends;
    return t;
  }

  private IElementType nl() {
    if (insertTerm) {
      insertTerm = false;
      return AlloyElementTypes.TERMINATOR;
    }
    return TokenType.WHITE_SPACE;
  }
%}

LineTerminator      = \r|\n|\r\n
NonNL               = [^\r\n]
WhiteSpaceNoNL      = [\ \t\f]

LineComment         = "//" {NonNL}*
BlockComment        = "/*" ([^*] | "*"+ [^*/])* "*"+ "/"

Letter              = [:jletter:] | "_"
LetterOrDigit       = [:jletterdigit:]
Identifier          = {Letter} {LetterOrDigit}*

Digits              = [0-9]+
DecimalLit          = {Digits}
Exponent            = [eE] [+\-]? {Digits}
FloatLit            = ({Digits} "." {Digits}? {Exponent}?) | ("." {Digits} {Exponent}?) | ({Digits} {Exponent})

// Go-style double-quoted string with escapes. No raw newline allowed inside.
EscapeSeq           = \\ ([abfnrtv\\\"\'] | [0-7]{3} | "x" [0-9a-fA-F]{2} | "u" [0-9a-fA-F]{4} | "U" [0-9a-fA-F]{8})
StringLit           = \" ([^\"\\\r\n] | {EscapeSeq})* \"
RawStringLit        = \` [^\`]* \`

%%

{WhiteSpaceNoNL}+        { return TokenType.WHITE_SPACE; }
{LineTerminator}         { return nl(); }

{LineComment}            { return term(AlloyElementTypes.LINE_COMMENT, insertTerm); }
{BlockComment}           { return term(AlloyElementTypes.BLOCK_COMMENT, insertTerm); }

"true"                   { return term(AlloyElementTypes.BOOL, true); }
"false"                  { return term(AlloyElementTypes.BOOL, true); }
"null"                   { return term(AlloyElementTypes.NULL, true); }

{Identifier}             { return term(AlloyElementTypes.IDENT, true); }

{FloatLit}               { return term(AlloyElementTypes.FLOAT, true); }
{DecimalLit}             { return term(AlloyElementTypes.NUMBER, true); }

{StringLit}              { return term(AlloyElementTypes.STRING, true); }
{RawStringLit}           { return term(AlloyElementTypes.RAW_STRING, true); }

"||"                     { return term(AlloyElementTypes.OR, false); }
"&&"                     { return term(AlloyElementTypes.AND, false); }
"=="                     { return term(AlloyElementTypes.EQ, false); }
"!="                     { return term(AlloyElementTypes.NEQ, false); }
"<="                     { return term(AlloyElementTypes.LTE, false); }
">="                     { return term(AlloyElementTypes.GTE, false); }

"="                      { return term(AlloyElementTypes.ASSIGN, false); }
","                      { return term(AlloyElementTypes.COMMA, false); }
"."                      { return term(AlloyElementTypes.DOT, false); }
"!"                      { return term(AlloyElementTypes.NOT, false); }
"<"                      { return term(AlloyElementTypes.LT, false); }
">"                      { return term(AlloyElementTypes.GT, false); }
"+"                      { return term(AlloyElementTypes.PLUS, false); }
"-"                      { return term(AlloyElementTypes.MINUS, false); }
"*"                      { return term(AlloyElementTypes.STAR, false); }
"/"                      { return term(AlloyElementTypes.SLASH, false); }
"%"                      { return term(AlloyElementTypes.PERCENT, false); }
"^"                      { return term(AlloyElementTypes.CARET, false); }

"{"                      { return term(AlloyElementTypes.LCURLY, false); }
"}"                      { return term(AlloyElementTypes.RCURLY, true); }
"("                      { return term(AlloyElementTypes.LPAREN, false); }
")"                      { return term(AlloyElementTypes.RPAREN, true); }
"["                      { return term(AlloyElementTypes.LBRACK, false); }
"]"                      { return term(AlloyElementTypes.RBRACK, true); }

[^]                      { return TokenType.BAD_CHARACTER; }
