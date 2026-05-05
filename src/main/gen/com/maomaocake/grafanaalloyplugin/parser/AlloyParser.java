// This is a generated file. Not intended for manual editing.
package com.maomaocake.grafanaalloyplugin.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AlloyParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return alloyFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // DOT IDENT
  public static boolean access_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "access_expr")) return false;
    if (!nextTokenIs(builder_, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOT, IDENT);
    exit_section_(builder_, marker_, ACCESS_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // mul_expr  (add_op mul_expr)*
  public static boolean add_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "add_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ADD_EXPR, "<add expr>");
    result_ = mul_expr(builder_, level_ + 1);
    result_ = result_ && add_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (add_op mul_expr)*
  private static boolean add_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "add_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!add_expr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "add_expr_1", pos_)) break;
    }
    return true;
  }

  // add_op mul_expr
  private static boolean add_expr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "add_expr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = add_op(builder_, level_ + 1);
    result_ = result_ && mul_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // PLUS | MINUS
  static boolean add_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "add_op")) return false;
    if (!nextTokenIs(builder_, "", MINUS, PLUS)) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    return result_;
  }

  /* ********************************************************** */
  // TERMINATOR* body_contents?
  static boolean alloyFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "alloyFile")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = alloyFile_0(builder_, level_ + 1);
    result_ = result_ && alloyFile_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean alloyFile_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "alloyFile_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "alloyFile_0", pos_)) break;
    }
    return true;
  }

  // body_contents?
  private static boolean alloyFile_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "alloyFile_1")) return false;
    body_contents(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // cmp_expr  (AND cmp_expr)*
  public static boolean and_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, AND_EXPR, "<and expr>");
    result_ = cmp_expr(builder_, level_ + 1);
    result_ = result_ && and_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (AND cmp_expr)*
  private static boolean and_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!and_expr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "and_expr_1", pos_)) break;
    }
    return true;
  }

  // AND cmp_expr
  private static boolean and_expr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND);
    result_ = result_ && cmp_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // "[" TERMINATOR* (expression (COMMA TERMINATOR* expression)* COMMA? TERMINATOR*)? "]"
  public static boolean array_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr")) return false;
    if (!nextTokenIs(builder_, LBRACK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACK);
    result_ = result_ && array_expr_1(builder_, level_ + 1);
    result_ = result_ && array_expr_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACK);
    exit_section_(builder_, marker_, ARRAY_EXPR, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean array_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "array_expr_1", pos_)) break;
    }
    return true;
  }

  // (expression (COMMA TERMINATOR* expression)* COMMA? TERMINATOR*)?
  private static boolean array_expr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2")) return false;
    array_expr_2_0(builder_, level_ + 1);
    return true;
  }

  // expression (COMMA TERMINATOR* expression)* COMMA? TERMINATOR*
  private static boolean array_expr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && array_expr_2_0_1(builder_, level_ + 1);
    result_ = result_ && array_expr_2_0_2(builder_, level_ + 1);
    result_ = result_ && array_expr_2_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA TERMINATOR* expression)*
  private static boolean array_expr_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!array_expr_2_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "array_expr_2_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA TERMINATOR* expression
  private static boolean array_expr_2_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && array_expr_2_0_1_0_1(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean array_expr_2_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2_0_1_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "array_expr_2_0_1_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA?
  private static boolean array_expr_2_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2_0_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // TERMINATOR*
  private static boolean array_expr_2_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_expr_2_0_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "array_expr_2_0_3", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // IDENT "=" expression
  public static boolean attribute(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "attribute")) return false;
    if (!nextTokenIs(builder_, IDENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IDENT, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, ATTRIBUTE, result_);
    return result_;
  }

  /* ********************************************************** */
  // block_name block_label? block_body
  public static boolean block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block")) return false;
    if (!nextTokenIs(builder_, IDENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = block_name(builder_, level_ + 1);
    result_ = result_ && block_1(builder_, level_ + 1);
    result_ = result_ && block_body(builder_, level_ + 1);
    exit_section_(builder_, marker_, BLOCK, result_);
    return result_;
  }

  // block_label?
  private static boolean block_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1")) return false;
    block_label(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // "{" TERMINATOR* (statement (TERMINATOR+ statement)* TERMINATOR*)? "}"
  public static boolean block_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body")) return false;
    if (!nextTokenIs(builder_, LCURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LCURLY);
    result_ = result_ && block_body_1(builder_, level_ + 1);
    result_ = result_ && block_body_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RCURLY);
    exit_section_(builder_, marker_, BLOCK_BODY, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean block_body_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "block_body_1", pos_)) break;
    }
    return true;
  }

  // (statement (TERMINATOR+ statement)* TERMINATOR*)?
  private static boolean block_body_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_2")) return false;
    block_body_2_0(builder_, level_ + 1);
    return true;
  }

  // statement (TERMINATOR+ statement)* TERMINATOR*
  private static boolean block_body_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = statement(builder_, level_ + 1);
    result_ = result_ && block_body_2_0_1(builder_, level_ + 1);
    result_ = result_ && block_body_2_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (TERMINATOR+ statement)*
  private static boolean block_body_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_2_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!block_body_2_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "block_body_2_0_1", pos_)) break;
    }
    return true;
  }

  // TERMINATOR+ statement
  private static boolean block_body_2_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_2_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = block_body_2_0_1_0_0(builder_, level_ + 1);
    result_ = result_ && statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR+
  private static boolean block_body_2_0_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_2_0_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TERMINATOR);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "block_body_2_0_1_0_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean block_body_2_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_body_2_0_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "block_body_2_0_2", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // STRING
  public static boolean block_label(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_label")) return false;
    if (!nextTokenIs(builder_, STRING)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING);
    exit_section_(builder_, marker_, BLOCK_LABEL, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENT (DOT IDENT)*
  public static boolean block_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_name")) return false;
    if (!nextTokenIs(builder_, IDENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENT);
    result_ = result_ && block_name_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, BLOCK_NAME, result_);
    return result_;
  }

  // (DOT IDENT)*
  private static boolean block_name_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_name_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!block_name_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "block_name_1", pos_)) break;
    }
    return true;
  }

  // DOT IDENT
  private static boolean block_name_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_name_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOT, IDENT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // statement (TERMINATOR+ statement)* TERMINATOR*
  static boolean body_contents(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "body_contents")) return false;
    if (!nextTokenIs(builder_, IDENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = statement(builder_, level_ + 1);
    result_ = result_ && body_contents_1(builder_, level_ + 1);
    result_ = result_ && body_contents_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (TERMINATOR+ statement)*
  private static boolean body_contents_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "body_contents_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!body_contents_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "body_contents_1", pos_)) break;
    }
    return true;
  }

  // TERMINATOR+ statement
  private static boolean body_contents_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "body_contents_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = body_contents_1_0_0(builder_, level_ + 1);
    result_ = result_ && statement(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR+
  private static boolean body_contents_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "body_contents_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, TERMINATOR);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "body_contents_1_0_0", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean body_contents_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "body_contents_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "body_contents_2", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // "(" expression_list? ")"
  public static boolean call_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "call_expr")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && call_expr_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, CALL_EXPR, result_);
    return result_;
  }

  // expression_list?
  private static boolean call_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "call_expr_1")) return false;
    expression_list(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // add_expr  (cmp_op add_expr)*
  public static boolean cmp_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cmp_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CMP_EXPR, "<cmp expr>");
    result_ = add_expr(builder_, level_ + 1);
    result_ = result_ && cmp_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (cmp_op add_expr)*
  private static boolean cmp_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cmp_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!cmp_expr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "cmp_expr_1", pos_)) break;
    }
    return true;
  }

  // cmp_op add_expr
  private static boolean cmp_expr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cmp_expr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = cmp_op(builder_, level_ + 1);
    result_ = result_ && add_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // EQ | NEQ | LT | LTE | GT | GTE
  static boolean cmp_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "cmp_op")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, GTE);
    return result_;
  }

  /* ********************************************************** */
  // or_expr
  public static boolean expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPRESSION, "<expression>");
    result_ = or_expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // expression (COMMA expression)* COMMA?
  static boolean expression_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && expression_list_1(builder_, level_ + 1);
    result_ = result_ && expression_list_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA expression)*
  private static boolean expression_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!expression_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "expression_list_1", pos_)) break;
    }
    return true;
  }

  // COMMA expression
  private static boolean expression_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // COMMA?
  private static boolean expression_list_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // field_key "=" expression
  public static boolean field(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "field")) return false;
    if (!nextTokenIs(builder_, "<field>", IDENT, STRING)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD, "<field>");
    result_ = field_key(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // IDENT | STRING
  public static boolean field_key(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "field_key")) return false;
    if (!nextTokenIs(builder_, "<field key>", IDENT, STRING)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FIELD_KEY, "<field key>");
    result_ = consumeToken(builder_, IDENT);
    if (!result_) result_ = consumeToken(builder_, STRING);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // IDENT
  public static boolean identifier_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "identifier_expr")) return false;
    if (!nextTokenIs(builder_, IDENT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENT);
    exit_section_(builder_, marker_, IDENTIFIER_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // "[" expression "]"
  public static boolean index_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "index_expr")) return false;
    if (!nextTokenIs(builder_, LBRACK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACK);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACK);
    exit_section_(builder_, marker_, INDEX_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // STRING | RAW_STRING | NUMBER | FLOAT | BOOL | NULL
  public static boolean literal_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literal_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LITERAL_EXPR, "<literal expr>");
    result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = consumeToken(builder_, RAW_STRING);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, FLOAT);
    if (!result_) result_ = consumeToken(builder_, BOOL);
    if (!result_) result_ = consumeToken(builder_, NULL);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // pow_expr  (mul_op pow_expr)*
  public static boolean mul_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mul_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MUL_EXPR, "<mul expr>");
    result_ = pow_expr(builder_, level_ + 1);
    result_ = result_ && mul_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (mul_op pow_expr)*
  private static boolean mul_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mul_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!mul_expr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "mul_expr_1", pos_)) break;
    }
    return true;
  }

  // mul_op pow_expr
  private static boolean mul_expr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mul_expr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = mul_op(builder_, level_ + 1);
    result_ = result_ && pow_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // STAR | SLASH | PERCENT
  static boolean mul_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "mul_op")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    return result_;
  }

  /* ********************************************************** */
  // "{" TERMINATOR* (field (COMMA TERMINATOR* field)* COMMA? TERMINATOR*)? "}"
  public static boolean object_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr")) return false;
    if (!nextTokenIs(builder_, LCURLY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LCURLY);
    result_ = result_ && object_expr_1(builder_, level_ + 1);
    result_ = result_ && object_expr_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RCURLY);
    exit_section_(builder_, marker_, OBJECT_EXPR, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean object_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "object_expr_1", pos_)) break;
    }
    return true;
  }

  // (field (COMMA TERMINATOR* field)* COMMA? TERMINATOR*)?
  private static boolean object_expr_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2")) return false;
    object_expr_2_0(builder_, level_ + 1);
    return true;
  }

  // field (COMMA TERMINATOR* field)* COMMA? TERMINATOR*
  private static boolean object_expr_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = field(builder_, level_ + 1);
    result_ = result_ && object_expr_2_0_1(builder_, level_ + 1);
    result_ = result_ && object_expr_2_0_2(builder_, level_ + 1);
    result_ = result_ && object_expr_2_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA TERMINATOR* field)*
  private static boolean object_expr_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!object_expr_2_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "object_expr_2_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA TERMINATOR* field
  private static boolean object_expr_2_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && object_expr_2_0_1_0_1(builder_, level_ + 1);
    result_ = result_ && field(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // TERMINATOR*
  private static boolean object_expr_2_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2_0_1_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "object_expr_2_0_1_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA?
  private static boolean object_expr_2_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2_0_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  // TERMINATOR*
  private static boolean object_expr_2_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_expr_2_0_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, TERMINATOR)) break;
      if (!empty_element_parsed_guard_(builder_, "object_expr_2_0_3", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // primary_expr oper_tail*
  public static boolean oper_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oper_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OPER_EXPR, "<oper expr>");
    result_ = primary_expr(builder_, level_ + 1);
    result_ = result_ && oper_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // oper_tail*
  private static boolean oper_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oper_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!oper_tail(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "oper_expr_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // access_expr | index_expr | call_expr
  static boolean oper_tail(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "oper_tail")) return false;
    boolean result_;
    result_ = access_expr(builder_, level_ + 1);
    if (!result_) result_ = index_expr(builder_, level_ + 1);
    if (!result_) result_ = call_expr(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // and_expr  (OR  and_expr)*
  public static boolean or_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OR_EXPR, "<or expr>");
    result_ = and_expr(builder_, level_ + 1);
    result_ = result_ && or_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (OR  and_expr)*
  private static boolean or_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expr_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!or_expr_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "or_expr_1", pos_)) break;
    }
    return true;
  }

  // OR  and_expr
  private static boolean or_expr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OR);
    result_ = result_ && and_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // "(" expression ")"
  public static boolean paren_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "paren_expr")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, PAREN_EXPR, result_);
    return result_;
  }

  /* ********************************************************** */
  // unary_expr (CARET pow_expr)?
  public static boolean pow_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pow_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, POW_EXPR, "<pow expr>");
    result_ = unary_expr(builder_, level_ + 1);
    result_ = result_ && pow_expr_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (CARET pow_expr)?
  private static boolean pow_expr_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pow_expr_1")) return false;
    pow_expr_1_0(builder_, level_ + 1);
    return true;
  }

  // CARET pow_expr
  private static boolean pow_expr_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pow_expr_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CARET);
    result_ = result_ && pow_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // literal_expr
  //                | identifier_expr
  //                | paren_expr
  //                | array_expr
  //                | object_expr
  public static boolean primary_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "primary_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PRIMARY_EXPR, "<primary expr>");
    result_ = literal_expr(builder_, level_ + 1);
    if (!result_) result_ = identifier_expr(builder_, level_ + 1);
    if (!result_) result_ = paren_expr(builder_, level_ + 1);
    if (!result_) result_ = array_expr(builder_, level_ + 1);
    if (!result_) result_ = object_expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // attribute | block
  public static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENT, "<statement>");
    result_ = attribute(builder_, level_ + 1);
    if (!result_) result_ = block(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, AlloyParser::statement_recover);
    return result_;
  }

  /* ********************************************************** */
  // !(TERMINATOR | RCURLY)
  static boolean statement_recover(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_recover")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NOT_);
    result_ = !statement_recover_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // TERMINATOR | RCURLY
  private static boolean statement_recover_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_recover_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, TERMINATOR);
    if (!result_) result_ = consumeToken(builder_, RCURLY);
    return result_;
  }

  /* ********************************************************** */
  // (MINUS | NOT) unary_expr | oper_expr
  public static boolean unary_expr(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expr")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, UNARY_EXPR, "<unary expr>");
    result_ = unary_expr_0(builder_, level_ + 1);
    if (!result_) result_ = oper_expr(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (MINUS | NOT) unary_expr
  private static boolean unary_expr_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expr_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unary_expr_0_0(builder_, level_ + 1);
    result_ = result_ && unary_expr(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // MINUS | NOT
  private static boolean unary_expr_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expr_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, NOT);
    return result_;
  }

}
