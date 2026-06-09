package com.maomaocake.grafanaalloyplugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
            "did not expect Alloy completions inside an inline (non-block) scalar, got $lookups",
            lookups.any { it.startsWith("prometheus.") },
        )
    }
}
