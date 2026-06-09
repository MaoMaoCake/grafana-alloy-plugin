package com.maomaocake.grafanaalloyplugin.injection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLQuotedText

/**
 * Flags `config.alloy: "..."` (and `*.alloy:`) values written as quoted scalars and offers
 * a quick fix that converts them to a `|` block scalar.
 *
 * Why this matters: `kubectl get cm -o yaml` and the bundled Kubernetes plugin's "View YAML"
 * action round-trip ConfigMaps as quoted scalars with `\n` escapes. The platform's literal
 * text escaper marks single-physical-line quoted scalars as one-line, which means our
 * `MultiHostInjector` registers but the injection never materialises (Alloy is
 * newline-significant). Once the user accepts the fix and the value becomes a `|` block,
 * highlighting / completion / references / inspections / Cmd-Q docs all light up.
 *
 * Only applies when the decoded value contains a newline — a genuinely single-line Alloy
 * config (rare but possible) doesn't need the conversion.
 */
class AlloyYamlBlockScalarInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        // The platform drives `LocalInspectionTool` visitors via `visitElement` per-element,
        // not via the language-specific `accept(...)` dispatch — so subclassing
        // `YamlPsiElementVisitor` and overriding `visitQuotedText` would silently never
        // fire. Plain `PsiElementVisitor` + an `instanceof` check is the supported pattern.
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is YAMLQuotedText) return
                val keyValue = element.parent as? YAMLKeyValue ?: return
                val key = keyValue.keyText
                if (!isAlloyKey(key)) return

                val decoded = element.textValue
                if ('\n' !in decoded) return

                holder.registerProblem(
                    element,
                    "Alloy config in quoted scalar — convert to a `|` block for full editor support",
                    ConvertToBlockScalarFix(),
                )
            }
        }

    private fun isAlloyKey(key: String): Boolean =
        key == "config.alloy" || key.endsWith(".alloy")
}

private class ConvertToBlockScalarFix : LocalQuickFix {
    override fun getFamilyName(): String = "Convert to YAML `|` block scalar"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val quoted = descriptor.psiElement as? YAMLQuotedText ?: return
        val keyValue = quoted.parent as? YAMLKeyValue ?: return

        // `getTextValue` returns the decoded content (escapes resolved). Splitting on `\n`
        // is enough — YAML normalises CRLF on read.
        val decoded = quoted.textValue
        val lines = decoded.split('\n')

        // The key sits at some column; the block contents must indent by at least one more
        // space than the key. We use key column + 2 to match the IDE's default YAML style.
        val keyOffset = keyValue.textOffset
        val document = PsiDocumentManager.getInstance(project).getDocument(keyValue.containingFile) ?: return
        val keyLineStart = document.getLineStartOffset(document.getLineNumber(keyOffset))
        val keyColumn = keyOffset - keyLineStart
        val indent = " ".repeat(keyColumn + 2)

        // YAMLElementGenerator can build us a fresh KV with whatever literal text we hand it,
        // including a `|` block. Building the body manually is fine — generator parses it.
        val body = buildString {
            append("|\n")
            for ((i, line) in lines.withIndex()) {
                // Trailing-empty-line handling: a value ending in `\n` decodes to a trailing
                // empty string in `lines`. Skip it so the block scalar doesn't gain an extra
                // empty line that the user didn't author.
                if (i == lines.lastIndex && line.isEmpty()) continue
                append(indent)
                append(line)
                append('\n')
            }
        }

        // `createYamlKeyValue(k, v)` would escape `v` back into a quoted scalar — defeating
        // the whole point. Instead, build the literal YAML source we want as a dummy file
        // and pluck the resulting KV out. That way the parser produces a real `|` block
        // scalar PSI subtree.
        val dummyText = "${keyValue.keyText}: $body"
        val generator = YAMLElementGenerator.getInstance(project)
        val dummyFile = generator.createDummyYamlWithText(dummyText)
        val replacement = PsiTreeUtil.findChildOfType(dummyFile, YAMLKeyValue::class.java) ?: return

        WriteCommandAction.runWriteCommandAction(project, "Convert to Block Scalar", null, {
            keyValue.replace(replacement)
        }, keyValue.containingFile)
    }
}
