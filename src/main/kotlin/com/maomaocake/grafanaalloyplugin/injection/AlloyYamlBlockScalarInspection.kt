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
 * Why this matters: hand-pasted snippets (`config.alloy: "prom.scrape \"x\" {}\n..."`) end
 * up as **single-physical-line** quoted scalars. The platform's literal-text escaper marks
 * those one-line, so injection registers but never materialises and Alloy loses its line
 * breaks. Multi-physical-line wrapped quoted scalars (what `kubectl get cm -o yaml` and the
 * bundled Kubernetes plugin produce) inject natively; those are auto-converted on file open
 * by [AlloyAutoConvertOnOpen] when the project setting allows.
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
                if (!shouldConvertToBlockScalar(element)) return
                holder.registerProblem(
                    element,
                    "Alloy config in quoted scalar — convert to a `|` block for full editor support",
                    ConvertToBlockScalarFix(),
                )
            }
        }
}

private class ConvertToBlockScalarFix : LocalQuickFix {
    override fun getFamilyName(): String = "Convert to YAML `|` block scalar"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val quoted = descriptor.psiElement as? YAMLQuotedText ?: return
        WriteCommandAction.runWriteCommandAction(
            project,
            "Convert to Block Scalar",
            /* groupId = */ null,
            { convertQuotedToBlockScalar(project, quoted) },
            quoted.containingFile,
        )
    }
}

/**
 * `true` when [element] is a quoted scalar under a `config.alloy` / `*.alloy` key whose
 * decoded value spans multiple lines — the population [ConvertToBlockScalarFix] handles.
 *
 * Shared with [AlloyAutoConvertOnOpen] so the inspection and the auto-converter use the
 * same matching rule. If they drift, "the inspection said no but auto-convert did it
 * anyway" surprises happen.
 */
internal fun shouldConvertToBlockScalar(element: PsiElement): Boolean {
    if (element !is YAMLQuotedText) return false
    val keyValue = element.parent as? YAMLKeyValue ?: return false
    if (!isAlloyKey(keyValue.keyText)) return false
    return '\n' in element.textValue
}

internal fun isAlloyKey(key: String): Boolean =
    key == "config.alloy" || key.endsWith(".alloy")

/**
 * Replaces a quoted Alloy scalar with the equivalent `|` block scalar in place.
 *
 * Caller owns the write action — both the inspection's quick fix and the file-open
 * auto-converter wrap this in their own [WriteCommandAction] so the operation lands in
 * one undo step (the quick fix), or batches over many scalars in a single document pass
 * (the auto-converter).
 */
internal fun convertQuotedToBlockScalar(project: Project, quoted: YAMLQuotedText) {
    val keyValue = quoted.parent as? YAMLKeyValue ?: return

    // `getTextValue` returns the decoded content (escapes resolved). Splitting on `\n`
    // is enough — YAML normalises CRLF on read.
    val decoded = quoted.textValue
    val lines = decoded.split('\n')

    // The key sits at some column; the block contents must indent by at least one more
    // space than the key. Key column + 2 matches IntelliJ's default YAML style.
    val keyOffset = keyValue.textOffset
    val document = PsiDocumentManager.getInstance(project).getDocument(keyValue.containingFile) ?: return
    val keyLineStart = document.getLineStartOffset(document.getLineNumber(keyOffset))
    val keyColumn = keyOffset - keyLineStart
    val indent = " ".repeat(keyColumn + 2)

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
    // and pluck the resulting KV out so the parser produces a real `|` block PSI subtree.
    val dummyText = "${keyValue.keyText}: $body"
    val generator = YAMLElementGenerator.getInstance(project)
    val dummyFile = generator.createDummyYamlWithText(dummyText)
    val replacement = PsiTreeUtil.findChildOfType(dummyFile, YAMLKeyValue::class.java) ?: return
    keyValue.replace(replacement)
}
