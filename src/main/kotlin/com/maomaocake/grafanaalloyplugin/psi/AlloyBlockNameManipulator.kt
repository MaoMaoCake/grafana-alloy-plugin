package com.maomaocake.grafanaalloyplugin.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.AlloyFileType

/**
 * Lets the platform rewrite the text of an [AlloyBlockName] — used when renaming a `declare`
 * label propagates to the invocation side (e.g. `add "default" {}` when `declare "add" {}` is
 * renamed to `sum`).
 */
class AlloyBlockNameManipulator : AbstractElementManipulator<AlloyBlockName>() {
    override fun handleContentChange(
        element: AlloyBlockName,
        range: TextRange,
        newContent: String,
    ): AlloyBlockName {
        val oldText = element.text
        val newText = oldText.substring(0, range.startOffset) + newContent + oldText.substring(range.endOffset)
        val src = "$newText \"__rename\" {}\n"
        val file = PsiFileFactory.getInstance(element.project)
            .createFileFromText("dummy.alloy", AlloyFileType, src)
        val fresh = PsiTreeUtil.findChildOfType(file, AlloyBlockName::class.java) ?: return element
        return element.replace(fresh) as AlloyBlockName
    }
}
