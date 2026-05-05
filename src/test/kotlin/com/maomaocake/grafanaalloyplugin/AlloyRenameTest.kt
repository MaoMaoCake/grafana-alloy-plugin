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
}
