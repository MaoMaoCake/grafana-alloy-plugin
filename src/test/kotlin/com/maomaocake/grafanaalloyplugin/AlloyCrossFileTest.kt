package com.maomaocake.grafanaalloyplugin

import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Verifies that references, reference completion and rename work across sibling `*.alloy`
 * files in the same directory (matching `alloy validate <dir>`'s scoping rule).
 */
class AlloyCrossFileTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/crossFile"

    fun testDottedReferenceResolvesToSiblingFile() {
        myFixture.configureByFiles("caller.alloy", "defs.alloy")

        val oper = PsiTreeUtil.findChildrenOfType(myFixture.file, AlloyOperExpr::class.java)
            .first {
                AlloyPsiUtil.identChain(it)?.joinToString(".") { n -> n.text } ==
                    "prometheus.remote_write.rw.receiver"
            }

        val resolved = oper.references.firstNotNullOfOrNull { it.resolve() } as? AlloyBlockLabel
        assertNotNull("expected cross-file resolve to succeed", resolved)
        val targetFile = resolved!!.containingFile
        assertEquals("defs.alloy", targetFile.name)
    }

    fun testDeclareInvocationResolvesAcrossFiles() {
        myFixture.configureByFiles("declareCaller.alloy", "declareDefs.alloy")

        val blockName = PsiTreeUtil.findChildrenOfType(myFixture.file, AlloyBlockName::class.java)
            .first { AlloyPsiUtil.blockNameIdents(it) == listOf("pipeline") }

        val resolved = blockName.references.firstNotNullOfOrNull { it.resolve() } as? AlloyBlockLabel
        assertNotNull("expected `pipeline` invocation to resolve to sibling file's declare", resolved)
        assertEquals("declareDefs.alloy", resolved!!.containingFile.name)
    }

    fun testForwardToCompletionFindsSiblingMetricsReceiver() {
        myFixture.configureByFiles("completeCaller.alloy", "completeDefs.alloy")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected `prometheus.remote_write.metrics_sink.receiver` in cross-file forward_to completion, got ${lookups.take(10)}",
            lookups.any { it == "prometheus.remote_write.metrics_sink.receiver" },
        )
        assertFalse(
            "logs-sink receiver must not show up for a metrics forward_to, got $lookups",
            lookups.any { it.startsWith("loki.write") },
        )
    }

    fun testRenamePropagatesAcrossFiles() {
        myFixture.configureByFiles("caller.alloy", "defs.alloy")
        // Find the declaring label (lives in defs.alloy, a non-open fixture file). The PSI for
        // sibling files is reachable via PsiManager.
        val dir = myFixture.file.virtualFile.parent
        val defsVf = dir.findChild("defs.alloy") ?: error("defs.alloy not in fixture dir")
        val defsPsi = PsiManager.getInstance(project).findFile(defsVf) as AlloyFile
        val label = PsiTreeUtil.findChildrenOfType(defsPsi, AlloyBlock::class.java)
            .first { AlloyPsiUtil.blockNameIdents(it.blockName) == listOf("prometheus", "remote_write") }
            .blockLabel!!

        myFixture.renameElement(label, "renamed")

        // The caller.alloy reference should now read `...renamed.receiver`.
        assertTrue(
            "expected caller.alloy to be rewritten to `renamed`, got:\n${myFixture.file.text}",
            myFixture.file.text.contains("prometheus.remote_write.renamed.receiver"),
        )
    }
}
