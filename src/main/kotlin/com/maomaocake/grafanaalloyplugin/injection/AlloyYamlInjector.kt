package com.maomaocake.grafanaalloyplugin.injection

import com.intellij.injected.editor.InjectionMeta
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.AlloyLanguage
import org.jetbrains.yaml.psi.YAMLBlockScalar
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLBlockScalarImpl
import org.jetbrains.yaml.psi.impl.YAMLScalarImpl

/**
 * Injects [AlloyLanguage] into YAML scalar values whose key is `config.alloy` or
 * matches `*.alloy`. Activates on:
 *
 *  - **Block scalars** (`|`, `|-`, `|+`, `>`) — the hand-authored / Helm-rendered form.
 *  - **Multi-physical-line quoted scalars** — the form `kubectl get cm -o yaml` and the
 *    Kubernetes plugin's "View YAML" produce, where the value wraps across several editor
 *    lines so the YAML escape decoder is happy to expose multi-line decoded content.
 *
 * **Single-physical-line quoted scalars are left alone.** The platform's literal-text
 * escaper for those reports `isOneLine() = true` (it checks the *raw* source for a literal
 * LF, which a one-line `"...\n..."` doesn't have). The injection layer then refuses to
 * mount multi-line content on a one-line host, so injection registers but never
 * materialises. Users in that bucket get a separate inspection
 * ([AlloyYamlBlockScalarInspection]) suggesting "Convert to `|` block scalar."
 *
 * Loaded via the optional `alloy-yaml.xml` config file, so the JVM never sees this class
 * in IDEs that ship without YAML support.
 */
class AlloyYamlInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(YAMLScalar::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        if (host !is YAMLScalar) return
        if (host !is YAMLScalarImpl) return
        if (!host.isValidHost) return
        if (!isInjectableScalarShape(host)) return

        val keyValue = host.parent as? YAMLKeyValue ?: return
        if (!isAlloyKey(keyValue.keyText)) return

        // Replicates `org.jetbrains.yaml.YamlLanguageInjectionPerformer.injectIntoYamlMultiRanges`
        // (which lives in `intellij.yaml.backend.jar` and isn't on our compile classpath).
        // The shape-specific bits:
        //  - Block scalars: stash the indent string in user data so the injected document
        //    is indented correctly when the platform stitches fragments back together.
        //  - First range gets the prefix (none here), last range gets the suffix (none here),
        //    middle ranges get plain (null, null). No newline glue between ranges — the
        //    platform's literal-text escaper decodes wraps correctly when the range list
        //    is faithful to `getContentRanges`.
        val ranges: List<TextRange> = host.contentRanges
        if (ranges.isEmpty()) return

        registrar.startInjecting(AlloyLanguage)
        if (host is YAMLBlockScalarImpl) {
            host.putUserData(InjectionMeta.getInjectionIndent(), host.indentString)
        }
        if (ranges.size == 1) {
            registrar.addPlace(null, null, host, ranges.single())
        } else {
            registrar.addPlace(null, null, host, ranges.first())
            for (range in ranges.subList(1, ranges.size - 1)) {
                registrar.addPlace(null, null, host, range)
            }
            registrar.addPlace(null, null, host, ranges.last())
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

    /**
     * `true` for scalars where the platform's literal-text escaper will accept multi-line
     * injected content. Block scalars always qualify. A quoted scalar qualifies only if its
     * **raw** source already spans multiple physical lines — that's what tells the escaper
     * (`isMultiline` / `isOneLine`) it's allowed to host multi-line injected text. Tooling
     * like `kubectl get cm -o yaml` produces exactly that shape (escaped `\n` *and* the
     * scalar wrapped across editor lines for readability).
     *
     * Single-line `"..."` with only escaped `\n` doesn't qualify; that's the inspection's
     * domain.
     */
    private fun isInjectableScalarShape(host: YAMLScalar): Boolean {
        if (host is YAMLBlockScalar) return true
        if (host is YAMLQuotedText) return host.isMultiline
        return false
    }
}
