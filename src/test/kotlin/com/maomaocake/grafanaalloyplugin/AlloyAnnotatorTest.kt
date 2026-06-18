package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AlloyAnnotatorTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/references"

    fun testUnresolvedReferenceFlagged() {
        myFixture.configureByFile("unresolved.alloy")
        myFixture.checkHighlighting(/* checkWarnings = */ true, /* checkInfos = */ false, /* checkWeakWarnings = */ false)
    }

    fun testDuplicateLabelFlagged() {
        myFixture.configureByFile("duplicate.alloy")
        myFixture.checkHighlighting(/* checkWarnings = */ false, /* checkInfos = */ false, /* checkWeakWarnings = */ false)
    }

    fun testUnknownArgFlagged() {
        myFixture.configureByFile("unknownArg.alloy")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testUnknownNestedBlockFlagged() {
        myFixture.configureByFile("unknownBlock.alloy")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testLokiProcessJsonStageIsKnown() {
        myFixture.configureByFile("lokiProcessJsonStage.alloy")
        myFixture.checkHighlighting(true, false, false)
    }

    fun testMissingRequiredArgsFlagged() {
        myFixture.configureByFile("missingRequired.alloy")
        myFixture.checkHighlighting(false, false, false)
    }

    fun testPortTypeMismatchFlagged() {
        myFixture.configureByFile("portTypeMismatch.alloy")
        myFixture.checkHighlighting(true, false, false)
    }

    /** A `loki.write.X.receiver` reference with no matching `loki.write "X"` in the file is
     * unresolved. Confirms the annotator warns when the declaration is actually missing
     * (and proves the opposite case — see `AlloyReferenceTest.testLokiWritePipeline…`). */
    fun testMissingLokiWriteDeclarationFlagged() {
        myFixture.configureByFile("missingDeclaration.alloy")
        myFixture.checkHighlighting(true, false, false)
    }
}
