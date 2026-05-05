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

public class AlloyFieldKeyImpl extends ASTWrapperPsiElement implements AlloyFieldKey {

  public AlloyFieldKeyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull AlloyVisitor visitor) {
    visitor.visitFieldKey(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof AlloyVisitor) accept((AlloyVisitor)visitor);
    else super.accept(visitor);
  }

}
