// This is a generated file. Not intended for manual editing.
package com.maomaocake.grafanaalloyplugin.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AlloyPrimaryExpr extends AlloyPsiElement {

  @Nullable
  AlloyArrayExpr getArrayExpr();

  @Nullable
  AlloyIdentifierExpr getIdentifierExpr();

  @Nullable
  AlloyLiteralExpr getLiteralExpr();

  @Nullable
  AlloyObjectExpr getObjectExpr();

  @Nullable
  AlloyParenExpr getParenExpr();

}
