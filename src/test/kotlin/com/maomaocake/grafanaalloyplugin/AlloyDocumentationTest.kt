package com.maomaocake.grafanaalloyplugin

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.docs.AlloyDocumentationProvider
import com.maomaocake.grafanaalloyplugin.psi.AlloyAttribute
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

class AlloyDocumentationTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/docs"

    private val provider = AlloyDocumentationProvider()

    fun testComponentBlockDoc() {
        val file = myFixture.configureByFile("componentDoc.alloy")
        val block = PsiTreeUtil.findChildOfType(file, AlloyBlock::class.java)!!
        val doc = provider.generateDoc(block, null)
        assertNotNull(doc)
        val text = doc!!
        assertTrue("doc should mention prometheus.scrape, got: $text", text.contains("prometheus.scrape"))
        assertTrue("doc should mention an argument, got: $text", text.contains("forward_to"))
        assertTrue("doc should mention the docs URL, got: $text", text.contains("grafana.com/docs/alloy"))
    }

    fun testAttributeDoc() {
        val file = myFixture.configureByFile("componentDoc.alloy")
        val attr = PsiTreeUtil.findChildrenOfType(file, AlloyAttribute::class.java)
            .first { it.firstChild?.text == "forward_to" }
        val doc = provider.generateDoc(attr, null)
        assertNotNull(doc)
        val text = doc!!
        assertTrue("attr doc should mention forward_to, got: $text", text.contains("forward_to"))
        assertTrue("attr doc should show the Go type, got: $text", text.contains("storage.Appendable"))
        assertTrue("attr doc should note required, got: $text", text.contains(">yes<"))
    }

    fun testReferenceDoc() {
        myFixture.configureByText(
            "ref.alloy",
            """
            prometheus.remote_write "rw" {
                endpoint { url = "http://mimir/push" }
            }

            prometheus.scrape "s" {
                targets    = []
                forward_to = [prometheus.remote_write.rw.receiver]
            }
            """.trimIndent(),
        )
        val oper = PsiTreeUtil.findChildrenOfType(myFixture.file, AlloyOperExpr::class.java)
            .firstOrNull { oper ->
                val chain = AlloyPsiUtil.identChain(oper)?.joinToString(".") { it.text }
                chain == "prometheus.remote_write.rw.receiver"
            }!!
        val doc = provider.generateDoc(oper, null)
        assertNotNull(doc)
        val text = doc!!
        assertTrue("ref doc should reference the target component, got: $text", text.contains("prometheus.remote_write"))
        assertTrue("ref doc should name the export, got: $text", text.contains("receiver"))
    }
}
