package com.maomaocake.grafanaalloyplugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

class AlloyRenameTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/references"

    fun testRenameLabelUpdatesReferences() {
        val file = myFixture.configureByFile("rename_before.alloy")
        val labelToRename = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)
            .firstNotNullOfOrNull { b ->
                b.blockLabel?.takeIf { AlloyPsiUtil.unquoteLabel(it) == "container_exporter" }
            } ?: error("Couldn't find the container_exporter label")

        myFixture.renameElement(labelToRename, "renamed_exporter")
        myFixture.checkResultByFile("rename_after.alloy")
    }

    fun testRenameFromDeclarationUpdatesReferences() {
        val file = myFixture.configureByFile("rename_from_declaration_before.alloy")
        val labelToRename = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)
            .firstNotNullOfOrNull { b ->
                b.blockLabel?.takeIf { AlloyPsiUtil.unquoteLabel(it) == "d" }
            } ?: error("Couldn't find the \"d\" label")

        myFixture.renameElement(labelToRename, "renamed")
        myFixture.checkResultByFile("rename_from_declaration_after.alloy")
    }

    /**
     * Exercises the real IDE rename path: caret in a label's STRING leaf, go through
     * [com.intellij.codeInsight.TargetElementUtil] (like Shift+F6 in the editor does). Proves
     * that [com.maomaocake.grafanaalloyplugin.references.AlloyTargetElementEvaluator] surfaces
     * the block label as a rename target rather than leaving the menu item greyed out.
     */
    fun testRenameAtCaretInsideLabelString() {
        myFixture.configureByFile("rename_caret_before.alloy")
        myFixture.renameElementAtCaret("renamed")
        myFixture.checkResultByFile("rename_caret_after.alloy")
    }
}
