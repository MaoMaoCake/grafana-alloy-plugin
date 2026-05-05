package com.maomaocake.grafanaalloyplugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

class AlloyDeclareReferenceTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/references"

    fun testInvocationResolvesToDeclare() {
        val file = myFixture.configureByFile("declareModule.alloy")
        val invocationName = PsiTreeUtil.findChildrenOfType(file, AlloyBlockName::class.java)
            .firstOrNull { bn ->
                val parent = bn.parent as? AlloyBlock ?: return@firstOrNull false
                AlloyPsiUtil.blockNameIdents(bn) == listOf("add") &&
                    AlloyPsiUtil.blockNameIdents((parent).blockName) == listOf("add")
            } ?: error("Couldn't find `add \"default\"` invocation")

        val declare = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java).firstOrNull { b ->
            AlloyPsiUtil.blockNameIdents(b.blockName) == listOf("declare") &&
                b.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } == "add"
        } ?: error("Couldn't find `declare \"add\"` block")

        val resolved = invocationName.references.firstNotNullOfOrNull { it.resolve() }
        assertEquals(declare.blockLabel, resolved)
    }

    fun testRenameDeclareUpdatesInvocation() {
        val file = myFixture.configureByFile("declareRenameBefore.alloy")
        val declareLabel = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java).firstOrNull { b ->
            AlloyPsiUtil.blockNameIdents(b.blockName) == listOf("declare") &&
                b.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } == "add"
        }?.blockLabel ?: error("Couldn't find declare's label")

        myFixture.renameElement(declareLabel, "sum")
        myFixture.checkResultByFile("declareRenameAfter.alloy")
    }
}
