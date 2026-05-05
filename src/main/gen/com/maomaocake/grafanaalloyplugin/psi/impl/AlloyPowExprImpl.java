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

public class AlloyPowExprImpl extends ASTWrapperPsiElement implements AlloyPowExpr {

  public AlloyPowExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AlloyVisitor visitor) {
    visitor.visitPowExpr(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AlloyVisitor) accept((AlloyVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public AlloyPowExpr getPowExpr() {
    return findChildByClass(AlloyPowExpr.class);
  }

  @Override
  @NotNull
  public AlloyUnaryExpr getUnaryExpr() {
    return findNotNullChildByClass(AlloyUnaryExpr.class);
  }

}
