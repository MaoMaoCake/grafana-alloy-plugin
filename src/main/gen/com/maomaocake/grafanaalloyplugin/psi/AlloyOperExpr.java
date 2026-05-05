// This is a generated file. Not intended for manual editing.
package com.maomaocake.grafanaalloyplugin.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AlloyOperExpr extends AlloyPsiElement {

  @NotNull
  List<AlloyAccessExpr> getAccessExprList();

  @NotNull
  List<AlloyCallExpr> getCallExprList();

  @NotNull
  List<AlloyIndexExpr> getIndexExprList();

  @NotNull
  AlloyPrimaryExpr getPrimaryExpr();

}
