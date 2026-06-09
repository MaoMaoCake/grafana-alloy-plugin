package com.maomaocake.grafanaalloyplugin

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maomaocake.grafanaalloyplugin.injection.AlloyInjectionSettings
import com.maomaocake.grafanaalloyplugin.injection.AlloyYamlBlockScalarInspection
import com.maomaocake.grafanaalloyplugin.injection.convertQuotedToBlockScalar
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Verifies [com.maomaocake.grafanaalloyplugin.injection.AlloyYamlInjector] activates on
 * `config.alloy` and `*.alloy` keys in YAML block scalars, and is silent everywhere else.
 *
 * The cheapest "is injection live?" check is completion: Alloy's contributor only fires on
 * Alloy PSI, so an injected fragment offers `prometheus.scrape` etc. while a plain YAML
 * scalar does not. We don't compare full lookup sets here — that's covered by the dedicated
 * completion tests; we just need a positive signal that the language layer is reaching the
 * caret position.
 */
class AlloyYamlInjectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData/injection"

    fun testInjectsUnderConfigDotAlloy() {
        myFixture.configureByFile("configmap.yaml")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected Alloy completions inside `config.alloy:` scalar, got ${lookups.take(8)}",
            lookups.any { it.startsWith("prometheus.") },
        )
    }

    fun testInjectsUnderStarDotAlloyKey() {
        myFixture.configureByFile("customAlloyKey.yaml")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertTrue(
            "expected Alloy completions inside `prom-agent.alloy:` scalar, got ${lookups.take(8)}",
            lookups.any { it.startsWith("prometheus.") },
        )
    }

    fun testDoesNotInjectUnderNonAlloyKey() {
        myFixture.configureByFile("nonAlloyKey.yaml")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        // YAML key-completion can offer plenty of things, but none of them should be Alloy
        // component names. If injection wrongly fires here we'd see prometheus.* show up.
        assertFalse(
            "did not expect Alloy completions inside `config.yaml:` scalar, but got $lookups",
            lookups.any { it.startsWith("prometheus.") },
        )
    }

    fun testDoesNotInjectIntoSingleLineScalar() {
        myFixture.configureByFile("inlineScalar.yaml")
        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()
        assertFalse(
            "did not expect Alloy completions inside a single-line scalar, got $lookups",
            lookups.any { it.startsWith("prometheus.") },
        )
    }

    /**
     * Real-world `kubectl get cm -o yaml` output: a double-quoted scalar wrapped across
     * several physical lines, with `\n` escapes for the in-config line breaks. The raw
     * scalar contains literal LFs (the wraps), which is what makes the platform's escaper
     * accept multi-line injection.
     */
    fun testInjectsIntoKubectlStyleQuotedScalar() {
        myFixture.configureByFile("kubectlConfigMap.yaml")
        val ilm = InjectedLanguageManager.getInstance(project)

        // When injection works, `findElementAt(caret)` returns an element from the *injected*
        // Alloy PSI rather than the YAML host. We confirm injection by walking up from the
        // caret element and asserting we land in an Alloy file before reaching the YAML
        // PsiFile root.
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("no element at caret", element)
        val containing = element!!.containingFile
        assertEquals(
            "expected the element at the caret to live in an injected Alloy file " +
                "(containing language=${containing.language.id}, parent=${element.parent?.javaClass?.simpleName})",
            "Alloy",
            containing.language.id,
        )

        // Belt-and-braces: the host file's injection registry should also enumerate Alloy.
        val host = ilm.getInjectionHost(element)
        assertNotNull("no injection host for element at caret", host)
    }

    /**
     * `kubectl get cm -o yaml` (and the bundled Kubernetes plugin's "View YAML" action)
     * round-trip ConfigMaps as double-quoted scalars with `\n` escapes. We can't inject
     * into those directly — the platform's literal text escaper rejects multi-line content
     * in a single-physical-line scalar — so we surface a quick-fix that converts the
     * value to a `|` block scalar, after which injection works.
     */
    fun testQuotedScalarFlaggedWithConvertQuickFix() {
        myFixture.enableInspections(AlloyYamlBlockScalarInspection::class.java)
        // Auto-convert is on by default; disable it for this test so the file stays in
        // its original quoted form long enough for the inspection (and quick fix) to run.
        AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars = false
        myFixture.configureByText(
            "quoted.yaml",
            "data:\n  config.alloy: \"prometheus.<caret>scrape \\\"x\\\" {}\\nprometheus.remote_write \\\"rw\\\" {}\\n\"\n",
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "expected the quoted-scalar inspection to flag `config.alloy: \"...\"`, got ${highlights.map { it.description }}",
            highlights.any { it.description?.contains("quoted scalar") == true },
        )

        val intention = myFixture.findSingleIntention("Convert to YAML `|` block scalar")
        myFixture.launchAction(intention)

        val after = myFixture.file.text
        assertTrue(
            "expected a `|` block scalar after the fix, got:\n$after",
            after.contains("config.alloy: |"),
        )
        assertTrue(
            "expected decoded Alloy content after the fix, got:\n$after",
            after.contains("prometheus.scrape \"x\" {}") &&
                after.contains("prometheus.remote_write \"rw\" {}"),
        )
    }

    /**
     * The auto-convert listener runs the same `convertQuotedToBlockScalar` helper that the
     * inspection's quick fix uses. We exercise the helper directly rather than the listener
     * because `BasePlatformTestCase` uses a light project where `FileEditorManagerListener`
     * events don't reliably fire — testing the listener wrapper would test the platform's
     * event plumbing, not our logic.
     */
    fun testAutoConvertHelperRewritesQuotedConfigToBlockScalar() {
        // Disable the listener so we exercise the helper directly without racing the
        // listener's own write action on file open.
        AlloyInjectionSettings.getInstance(project).autoConvertQuotedScalars = false
        myFixture.configureByText(
            "cm.yaml",
            "apiVersion: v1\n" +
                "data:\n" +
                "  config.alloy: \"prom.scrape \\\"x\\\" {}\\nprom.write \\\"rw\\\" {}\\n\"\n" +
                "  errorkey: |\n" +
                "    prom.scrape \"x\" {}\n",
        )

        val quoteds = PsiTreeUtil.findChildrenOfType(myFixture.file, YAMLQuotedText::class.java).toList()
        assertEquals("expected exactly one quoted scalar in the fixture", 1, quoteds.size)

        WriteCommandAction.runWriteCommandAction(project) {
            convertQuotedToBlockScalar(project, quoteds.single())
        }

        val after = myFixture.file.text
        assertTrue(
            "expected `config.alloy: |` after auto-convert, got:\n$after",
            after.contains("config.alloy: |"),
        )
        assertTrue(
            "expected decoded body lines after auto-convert, got:\n$after",
            after.contains("prom.scrape \"x\" {}") && after.contains("prom.write \"rw\" {}"),
        )
        assertTrue(
            "auto-convert must not touch a key that was already a block scalar:\n$after",
            after.contains("errorkey: |"),
        )
    }
}
