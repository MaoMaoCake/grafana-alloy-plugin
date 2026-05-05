package com.maomaocake.grafanaalloyplugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

class AlloyReferenceTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/references"

    fun testCadvisorPipelineReferencesResolve() {
        val file = myFixture.configureByFile("cadvisorPipeline.alloy")

        val blocksByKey: Map<String, AlloyBlock> = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)
            .mapNotNull { block ->
                val label = block.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } ?: return@mapNotNull null
                val key = (AlloyPsiUtil.blockNameIdents(block.blockName) + label).joinToString(".")
                key to block
            }
            .toMap()

        val opers = PsiTreeUtil.findChildrenOfType(file, AlloyOperExpr::class.java)
            .filter { AlloyPsiUtil.identChain(it) != null }

        fun resolveChain(chain: String): AlloyBlock? {
            val oper = opers.firstOrNull {
                AlloyPsiUtil.identChain(it)?.joinToString(".") { n -> n.text } == chain
            } ?: error("No OperExpr with chain $chain")
            return oper.references.firstNotNullOfOrNull { it.resolve() }
                ?.let { PsiTreeUtil.getParentOfType(it, AlloyBlock::class.java) }
        }

        assertEquals(
            blocksByKey["prometheus.remote_write.rw_asdf87n89asdnf"],
            resolveChain("prometheus.remote_write.rw_asdf87n89asdnf.receiver"),
        )
        assertEquals(
            blocksByKey["prometheus.exporter.cadvisor.container_exporter_asdf87n89asdnf"],
            resolveChain("prometheus.exporter.cadvisor.container_exporter_asdf87n89asdnf.targets"),
        )
    }
}
