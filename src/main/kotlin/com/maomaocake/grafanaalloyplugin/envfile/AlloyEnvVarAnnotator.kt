package com.maomaocake.grafanaalloyplugin.envfile

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes

/**
 * Flags `${VAR}` placeholders inside Alloy strings whose `VAR` isn't a key in the configured
 * envfile. Only fires when an envfile is configured — no envfile → no warnings (so opting out
 * of the templating feature also opts out of the validation).
 */
class AlloyEnvVarAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val type = element.node.elementType
        if (type !== AlloyElementTypes.STRING && type !== AlloyElementTypes.RAW_STRING) return

        val project = element.project
        if (AlloyEnvFileSettings.getInstance(project).envFilePath.isBlank()) return

        val entries = AlloyEnvFile.getInstance(project).entries()
        if (entries.isEmpty()) return

        val base = element.textRange.startOffset
        val text = element.text
        var i = 0
        while (i < text.length - 1) {
            if (text[i] == '$' && text[i + 1] == '{') {
                // `\${...}` is an escape — treat it as literal text, skip past the `$`.
                if (i >= 1 && text[i - 1] == '\\') {
                    i++
                    continue
                }
                val close = text.indexOf('}', i + 2)
                if (close < 0) break
                val name = text.substring(i + 2, close)
                if (name.isNotEmpty() && !entries.containsKey(name)) {
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        "Unknown env variable: `$name` is not in the configured envfile",
                    )
                        .range(TextRange(base + i, base + close + 1))
                        .create()
                }
                i = close + 1
            } else {
                i++
            }
        }
    }
}
