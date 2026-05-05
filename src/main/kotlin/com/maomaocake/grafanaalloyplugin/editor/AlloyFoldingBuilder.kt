package com.maomaocake.grafanaalloyplugin.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.psi.AlloyArrayExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockBody
import com.maomaocake.grafanaalloyplugin.psi.AlloyObjectExpr

/**
 * Folds block bodies (`{ … }`), object literals, and array literals whenever they span more than
 * one line. Single-line brace/bracket pairs stay unfolded since collapsing them would hide less
 * text than the placeholder.
 */
class AlloyFoldingBuilder : FoldingBuilderEx(), DumbAware {

    override fun getPlaceholderText(node: ASTNode): String = when (node.psi) {
        is AlloyBlockBody -> "{…}"
        is AlloyObjectExpr -> "{…}"
        is AlloyArrayExpr  -> "[…]"
        else               -> "…"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    override fun buildFoldRegions(
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ): Array<FoldingDescriptor> {
        val result = mutableListOf<FoldingDescriptor>()
        val foldables = PsiTreeUtil.findChildrenOfAnyType(
            root,
            AlloyBlockBody::class.java,
            AlloyObjectExpr::class.java,
            AlloyArrayExpr::class.java,
        )
        for (element in foldables) {
            val range = element.textRange
            if (range.length < 2) continue
            val startLine = document.getLineNumber(range.startOffset)
            val endLine = document.getLineNumber(range.endOffset - 1)
            if (startLine == endLine) continue
            result += FoldingDescriptor(element.node, range)
        }
        return result.toTypedArray()
    }
}
