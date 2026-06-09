package com.maomaocake.grafanaalloyplugin.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.AlloyLanguage
import org.jetbrains.yaml.psi.YAMLBlockScalar
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl

/**
 * Injects [AlloyLanguage] into YAML scalar values whose key is `config.alloy` or matches
 * `*.alloy`. Activates only on block scalars (`|`, `|-`, `|+`, `>`) — single-line strings
 * are skipped because nobody puts an Alloy config inline.
 *
 * Why a `MultiHostInjector` rather than a `LanguageInjectionContributor`: `MultiHostInjector`
 * can drive injection through `YAMLScalar.contentRanges` directly, which means the YAML
 * platform handles block-scalar indent stripping for us. The injected Alloy PSI sees clean
 * source; editor coordinates map back through the host automatically — no per-line offset
 * math required for the validator's line:col round-trip.
 *
 * Loaded via the optional `alloy-yaml.xml` config file, so the JVM never sees this class
 * in IDEs that ship without YAML support.
 */
class AlloyYamlInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(YAMLScalar::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        if (host !is YAMLScalar) return
        // Only block scalars (`|`, `|-`, `|+`, `>`). The platform's plain-text impl also
        // extends `YAMLBlockScalarImpl`, so we filter through the public `YAMLBlockScalar`
        // interface instead — that one is implemented only by literal/folded scalars.
        if (host !is YAMLBlockScalar) return
        if (host !is YAMLBlockScalarImpl) return  // for the `getContentRanges` helper
        if (!host.isValidHost) return

        val keyValue = host.parent as? YAMLKeyValue ?: return
        val key = keyValue.keyText
        if (!isAlloyKey(key)) return

        val ranges: List<TextRange> = host.contentRanges
        if (ranges.isEmpty()) return

        // Single virtual document spanning every content range — that lets references that
        // span block-scalar lines (which they do all the time in Alloy) resolve cleanly. The
        // platform stitches the ranges into one buffer for the injected parser to see.
        registrar.startInjecting(AlloyLanguage)
        for (i in ranges.indices) {
            val range = ranges[i]
            val isLast = i == ranges.size - 1
            // Add a newline between fragments so YAML's line breaks survive into the injected
            // document — Alloy is newline-significant, and dropping these would merge logically
            // separate statements.
            val suffix = if (isLast) null else "\n"
            registrar.addPlace(/* prefix = */ null, /* suffix = */ suffix, host, range)
        }
        registrar.doneInjecting()
    }

    /**
     * Match policy (kept narrow on purpose):
     *  - `config.alloy` — the default key in the upstream Alloy Helm chart.
     *  - `*.alloy`     — any user-renamed variant (e.g. `prom-agent.alloy`).
     *
     * Anything else — `data:`, `script:`, `extra-config: |` — gets ignored. Loose matching
     * leads to false-positive injections that surprise users editing unrelated YAML.
     */
    private fun isAlloyKey(key: String): Boolean =
        key == "config.alloy" || key.endsWith(".alloy")
}
