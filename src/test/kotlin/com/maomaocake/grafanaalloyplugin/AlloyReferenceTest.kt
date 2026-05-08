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

    /** Regression: `loki.write.X.receiver` used to be mistakenly unresolved after an
     * audit-fix pass that filtered [AlloyBlockIndex] to top-level blocks. The top-level
     * check had been based on `block.parent?.parent is PsiFile`, but real-world top-level
     * blocks are nested under an `AlloyStatement` whose parent is the file — check that
     * that structure still resolves. */
    fun testLokiWritePipelineReferencesResolve() {
        val file = myFixture.configureByFile("lokiWritePipeline.alloy")

        val blocksByKey = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java)
            .mapNotNull { block ->
                val label = block.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } ?: return@mapNotNull null
                (AlloyPsiUtil.blockNameIdents(block.blockName) + label).joinToString(".") to block
            }
            .toMap()

        val opers = PsiTreeUtil.findChildrenOfType(file, AlloyOperExpr::class.java)

        fun resolveChain(chain: String): AlloyBlock? {
            val oper = opers.firstOrNull {
                AlloyPsiUtil.identChain(it)?.joinToString(".") { n -> n.text } == chain
            } ?: error("No OperExpr with chain $chain in test file")
            return oper.references.firstNotNullOfOrNull { it.resolve() }
                ?.let { PsiTreeUtil.getParentOfType(it, AlloyBlock::class.java) }
        }

        assertEquals(
            blocksByKey["loki.write.lw_asdf87n89asdnf"],
            resolveChain("loki.write.lw_asdf87n89asdnf.receiver"),
        )
        assertEquals(
            blocksByKey["local.file_match.files_asdf87n89asdnf"],
            resolveChain("local.file_match.files_asdf87n89asdnf.targets"),
        )
    }

    /**
     * `declare "foo" { … }` creates a self-contained scope:
     *   - A reference inside the body resolves only to blocks declared *inside that body*.
     *   - A reference at file top level does NOT see blocks that live inside a declare.
     *
     * The fixture has a `loki.write "lw_inner"` inside a declare and a `loki.write "lw_outer"`
     * at the file top level. The top-level `loki.source.file "toplevel"` references
     * `loki.write.lw_outer.receiver` — must resolve to the outer block. The in-declare
     * `loki.source.file "tmpfiles"` references `loki.write.lw_inner.receiver` — must resolve
     * to the inner one.
     */
    fun testDeclareScopingKeepsBlocksSeparate() {
        val file = myFixture.configureByFile("declareScoping.alloy")

        val outerWrite = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java).first {
            AlloyPsiUtil.blockNameIdents(it.blockName) == listOf("loki", "write") &&
                AlloyPsiUtil.unquoteLabel(it.blockLabel!!) == "lw_outer"
        }
        val innerWrite = PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java).first {
            AlloyPsiUtil.blockNameIdents(it.blockName) == listOf("loki", "write") &&
                AlloyPsiUtil.unquoteLabel(it.blockLabel!!) == "lw_inner"
        }

        val opers = PsiTreeUtil.findChildrenOfType(file, AlloyOperExpr::class.java)
            .filter { AlloyPsiUtil.identChain(it) != null }

        fun resolveToBlock(chainText: String): AlloyBlock? {
            val oper = opers.first {
                AlloyPsiUtil.identChain(it)?.joinToString(".") { n -> n.text } == chainText
            }
            return oper.references.firstNotNullOfOrNull { it.resolve() }
                ?.let { PsiTreeUtil.getParentOfType(it, AlloyBlock::class.java) }
        }

        assertEquals(
            "top-level reference must resolve to the top-level loki.write",
            outerWrite,
            resolveToBlock("loki.write.lw_outer.receiver"),
        )
        assertEquals(
            "in-declare reference must resolve to the in-declare loki.write",
            innerWrite,
            resolveToBlock("loki.write.lw_inner.receiver"),
        )
    }
}
