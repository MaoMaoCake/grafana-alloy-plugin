package com.maomaocake.grafanaalloyplugin.envfile

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.maomaocake.grafanaalloyplugin.AlloyLanguage
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes

/**
 * Completion for env-variable references inside Alloy string literals.
 *
 * Triggers when the caret sits inside a `${...}` placeholder within a STRING or RAW_STRING
 * token. Offers every key from the project's configured envfile. If the
 * "Show variable values in completion" setting is on, the popup also shows a truncated value
 * next to each key (useful when env vars aren't secrets).
 *
 * Alloy itself doesn't natively parse `${...}` — this is purely a templating convenience for
 * users whose configs get post-processed (e.g. by `envsubst`, a shell wrapper, or a CI step)
 * before being loaded. The substitution happens *outside* the plugin.
 */
class AlloyEnvVarCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.or(
                PlatformPatterns.psiElement(AlloyElementTypes.STRING).withLanguage(AlloyLanguage),
                PlatformPatterns.psiElement(AlloyElementTypes.RAW_STRING).withLanguage(AlloyLanguage),
            ),
            EnvVarCompletionProvider(),
        )
    }
}

private class EnvVarCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val project = parameters.position.project
        val settings = AlloyEnvFileSettings.getInstance(project)
        if (settings.envFilePath.isBlank()) return

        val token = parameters.position
        val caretInToken = parameters.offset - token.textRange.startOffset
        val range = findEnclosingPlaceholder(token, caretInToken) ?: return

        // Re-scope the result to the portion typed inside `${...}` so the matcher ignores the
        // framework's `IntellijIdeaRulezzz` dummy and matches on the user-typed prefix alone.
        // The real typed text lives in the *document*, not the modified PSI token.
        val documentText = parameters.editor.document.charsSequence
        val placeholderStart = token.textRange.startOffset + range.first
        val typedPrefix = documentText.subSequence(placeholderStart + 2, parameters.offset).toString()
        val scoped = result.withPrefixMatcher(typedPrefix)

        val entries = AlloyEnvFile.getInstance(project).entries()
        for ((name, value) in entries) {
            val tail = if (settings.showValuesInCompletion) "  — ${truncate(value, 60)}" else null
            scoped.addElement(
                LookupElementBuilder.create(name)
                    .withPresentableText(name)
                    .withTailText(tail, true)
                    .withTypeText("env", true),
            )
        }
    }

    /**
     * Given the raw text of a string token and an offset inside it, returns the range of the
     * `${...}` placeholder the caret sits in, or null if not inside one.
     *
     * Range is `startIndex..endIndexExclusive`, with `startIndex` pointing at `$` — so
     * `text.substring(range.first + 2, caretOffset)` is what the user has typed so far between
     * `${` and the caret.
     */
    private fun findEnclosingPlaceholder(token: PsiElement, caretInToken: Int): IntRange? {
        val text = token.text
        // Walk backward from the caret to find a `${`.
        var i = caretInToken - 1
        while (i >= 1) {
            if (text[i - 1] == '$' && text[i] == '{') {
                val start = i - 1
                // Confirm we haven't already passed a `}` in between.
                val close = text.indexOf('}', caretInToken)
                val nextCloseBeforeCaret = text.substring(start, caretInToken).indexOf('}')
                if (nextCloseBeforeCaret >= 0) return null
                val endExcl = if (close >= 0) close + 1 else caretInToken
                return start..endExcl
            }
            if (text[i] == '}') {
                // We hit a close brace before a matching open — caret isn't inside a placeholder.
                return null
            }
            i--
        }
        return null
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.substring(0, max - 1) + "…"
}
