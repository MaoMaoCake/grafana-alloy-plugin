// This is a generated file. Not intended for manual editing.
package com.maomaocake.grafanaalloyplugin.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.maomaocake.grafanaalloyplugin.psi.*;

public class AlloyPrimaryExprImpl extends ASTWrapperPsiElement implements AlloyPrimaryExpr {

  public AlloyPrimaryExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AlloyVisitor visitor) {
    visitor.visitPrimaryExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AlloyVisitor) accept((AlloyVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AlloyArrayExpr getArrayExpr() {
    return findChildByClass(AlloyArrayExpr.class);
  }

  @Override
  @Nullable
  public AlloyIdentifierExpr getIdentifierExpr() {
    return findChildByClass(AlloyIdentifierExpr.class);
  }

  @Override
  @Nullable
  public AlloyLiteralExpr getLiteralExpr() {
    return findChildByClass(AlloyLiteralExpr.class);
  }

  @Override
  @Nullable
  public AlloyObjectExpr getObjectExpr() {
    return findChildByClass(AlloyObjectExpr.class);
  }

  @Override
  @Nullable
  public AlloyParenExpr getParenExpr() {
    return findChildByClass(AlloyParenExpr.class);
  }

}
