// This is a generated file. Not intended for manual editing.
package com.maomaocake.grafanaalloyplugin.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface AlloyBlock extends AlloyPsiElement {

  @NotNull
  AlloyBlockBody getBlockBody();

  @Nullable
  AlloyBlockLabel getBlockLabel();

  @NotNull
  AlloyBlockName getBlockName();

}
