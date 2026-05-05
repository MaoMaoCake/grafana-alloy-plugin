// This is a generated file. Not intended for manual editing.
package com.maomaocake.grafanaalloyplugin.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.maomaocake.grafanaalloyplugin.psi.impl.*;

public interface AlloyElementTypes {

  IElementType ACCESS_EXPR = new AlloyElementType("ACCESS_EXPR");
  IElementType ADD_EXPR = new AlloyElementType("ADD_EXPR");
  IElementType AND_EXPR = new AlloyElementType("AND_EXPR");
  IElementType ARRAY_EXPR = new AlloyElementType("ARRAY_EXPR");
  IElementType ATTRIBUTE = new AlloyElementType("ATTRIBUTE");
  IElementType BLOCK = new AlloyElementType("BLOCK");
  IElementType BLOCK_BODY = new AlloyElementType("BLOCK_BODY");
  IElementType BLOCK_LABEL = new AlloyElementType("BLOCK_LABEL");
  IElementType BLOCK_NAME = new AlloyElementType("BLOCK_NAME");
  IElementType CALL_EXPR = new AlloyElementType("CALL_EXPR");
  IElementType CMP_EXPR = new AlloyElementType("CMP_EXPR");
  IElementType EXPRESSION = new AlloyElementType("EXPRESSION");
  IElementType FIELD = new AlloyElementType("FIELD");
  IElementType FIELD_KEY = new AlloyElementType("FIELD_KEY");
  IElementType IDENTIFIER_EXPR = new AlloyElementType("IDENTIFIER_EXPR");
  IElementType INDEX_EXPR = new AlloyElementType("INDEX_EXPR");
  IElementType LITERAL_EXPR = new AlloyElementType("LITERAL_EXPR");
  IElementType MUL_EXPR = new AlloyElementType("MUL_EXPR");
  IElementType OBJECT_EXPR = new AlloyElementType("OBJECT_EXPR");
  IElementType OPER_EXPR = new AlloyElementType("OPER_EXPR");
  IElementType OR_EXPR = new AlloyElementType("OR_EXPR");
  IElementType PAREN_EXPR = new AlloyElementType("PAREN_EXPR");
  IElementType POW_EXPR = new AlloyElementType("POW_EXPR");
  IElementType PRIMARY_EXPR = new AlloyElementType("PRIMARY_EXPR");
  IElementType STATEMENT = new AlloyElementType("STATEMENT");
  IElementType UNARY_EXPR = new AlloyElementType("UNARY_EXPR");

  IElementType AND = new AlloyTokenType("&&");
  IElementType ASSIGN = new AlloyTokenType("=");
  IElementType BLOCK_COMMENT = new AlloyTokenType("BLOCK_COMMENT");
  IElementType BOOL = new AlloyTokenType("BOOL");
  IElementType CARET = new AlloyTokenType("^");
  IElementType COMMA = new AlloyTokenType(",");
  IElementType DOT = new AlloyTokenType(".");
  IElementType EQ = new AlloyTokenType("==");
  IElementType FLOAT = new AlloyTokenType("FLOAT");
  IElementType GT = new AlloyTokenType(">");
  IElementType GTE = new AlloyTokenType(">=");
  IElementType IDENT = new AlloyTokenType("IDENT");
  IElementType LBRACK = new AlloyTokenType("[");
  IElementType LCURLY = new AlloyTokenType("{");
  IElementType LINE_COMMENT = new AlloyTokenType("LINE_COMMENT");
  IElementType LPAREN = new AlloyTokenType("(");
  IElementType LT = new AlloyTokenType("<");
  IElementType LTE = new AlloyTokenType("<=");
  IElementType MINUS = new AlloyTokenType("-");
  IElementType NEQ = new AlloyTokenType("!=");
  IElementType NOT = new AlloyTokenType("!");
  IElementType NULL = new AlloyTokenType("NULL");
  IElementType NUMBER = new AlloyTokenType("NUMBER");
  IElementType OR = new AlloyTokenType("||");
  IElementType PERCENT = new AlloyTokenType("%");
  IElementType PLUS = new AlloyTokenType("+");
  IElementType RAW_STRING = new AlloyTokenType("RAW_STRING");
  IElementType RBRACK = new AlloyTokenType("]");
  IElementType RCURLY = new AlloyTokenType("}");
  IElementType RPAREN = new AlloyTokenType(")");
  IElementType SLASH = new AlloyTokenType("/");
  IElementType STAR = new AlloyTokenType("*");
  IElementType STRING = new AlloyTokenType("STRING");
  IElementType TERMINATOR = new AlloyTokenType("TERMINATOR");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ACCESS_EXPR) {
        return new AlloyAccessExprImpl(node);
      }
      else if (type == ADD_EXPR) {
        return new AlloyAddExprImpl(node);
      }
      else if (type == AND_EXPR) {
        return new AlloyAndExprImpl(node);
      }
      else if (type == ARRAY_EXPR) {
        return new AlloyArrayExprImpl(node);
      }
      else if (type == ATTRIBUTE) {
        return new AlloyAttributeImpl(node);
      }
      else if (type == BLOCK) {
        return new AlloyBlockImpl(node);
      }
      else if (type == BLOCK_BODY) {
        return new AlloyBlockBodyImpl(node);
      }
      else if (type == BLOCK_LABEL) {
        return new AlloyBlockLabelImpl(node);
      }
      else if (type == BLOCK_NAME) {
        return new AlloyBlockNameImpl(node);
      }
      else if (type == CALL_EXPR) {
        return new AlloyCallExprImpl(node);
      }
      else if (type == CMP_EXPR) {
        return new AlloyCmpExprImpl(node);
      }
      else if (type == EXPRESSION) {
        return new AlloyExpressionImpl(node);
      }
      else if (type == FIELD) {
        return new AlloyFieldImpl(node);
      }
      else if (type == FIELD_KEY) {
        return new AlloyFieldKeyImpl(node);
      }
      else if (type == IDENTIFIER_EXPR) {
        return new AlloyIdentifierExprImpl(node);
      }
      else if (type == INDEX_EXPR) {
        return new AlloyIndexExprImpl(node);
      }
      else if (type == LITERAL_EXPR) {
        return new AlloyLiteralExprImpl(node);
      }
      else if (type == MUL_EXPR) {
        return new AlloyMulExprImpl(node);
      }
      else if (type == OBJECT_EXPR) {
        return new AlloyObjectExprImpl(node);
      }
      else if (type == OPER_EXPR) {
        return new AlloyOperExprImpl(node);
      }
      else if (type == OR_EXPR) {
        return new AlloyOrExprImpl(node);
      }
      else if (type == PAREN_EXPR) {
        return new AlloyParenExprImpl(node);
      }
      else if (type == POW_EXPR) {
        return new AlloyPowExprImpl(node);
      }
      else if (type == PRIMARY_EXPR) {
        return new AlloyPrimaryExprImpl(node);
      }
      else if (type == STATEMENT) {
        return new AlloyStatementImpl(node);
      }
      else if (type == UNARY_EXPR) {
        return new AlloyUnaryExprImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
