package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.io.File

/**
 * Runs `alloy validate` on the file's directory when trigger mode is `OnIdle`, maps the
 * parser's output back to editor annotations.
 *
 *  - [collectInformation] decides whether to run at all. Skips when Windows, when the file
 *    has no on-disk parent dir, or when the user picked manual trigger mode.
 *  - [doAnnotate] does the shellout off-EDT (the IntelliJ platform guarantees this is
 *    called on a background thread).
 *  - [apply] emits [HighlightSeverity.ERROR] annotations at the line:col the parser reports.
 *    Diagnostics without a location go to a single file-level annotation; unparseable output
 *    becomes a single ERROR at the file top so users see *something* instead of silence.
 */
class AlloyValidatorExternalAnnotator :
    ExternalAnnotator<AlloyValidatorExternalAnnotator.Input, AlloyValidatorExternalAnnotator.Result>() {

    data class Input(val file: PsiFile, val target: File)
    data class Result(val diagnostics: List<AlloyValidatorOutputParser.Diagnostic>, val rawStderr: String)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Input? {
        if (!AlloyValidatorAvailability.isSupportedOs) return null
        val project = file.project
        val settings = AlloyValidatorSettings.getInstance(project)
        if (settings.triggerMode != AlloyValidatorSettings.TriggerMode.OnIdle) return null

        val vf = file.virtualFile ?: return null
        return Input(file, File(vf.path))
    }

    override fun doAnnotate(collected: Input): Result? {
        val run = AlloyValidatorRunner.run(collected.file.project, collected.target)
        if (run.crashedBeforeRunning) return Result(emptyList(), run.failureReason ?: "")
        return Result(AlloyValidatorOutputParser.parse(run.stderr), run.stderr)
    }

    override fun apply(file: PsiFile, result: Result?, holder: AnnotationHolder) {
        if (result == null) return
        val doc = file.viewProvider.document ?: return
        val ourPath = file.virtualFile?.path ?: return

        // Only surface diagnostics that belong to *this* file — `alloy validate` runs on
        // the whole directory, so sibling files' errors are someone else's problem.
        val ours = result.diagnostics.filter { diag ->
            diag.path == null || diag.path == ourPath || diag.path.endsWith("/${file.name}")
        }

        if (ours.isEmpty()) return

        for (d in ours) {
            val range = rangeFor(doc, d) ?: TextRange(0, (doc.textLength).coerceAtLeast(0))
            holder.newAnnotation(HighlightSeverity.ERROR, "alloy validate: ${d.message}")
                .range(range)
                .create()
        }
    }

    /**
     * Turns a 1-based `(line, column)` into a [TextRange] for the token the column points at.
     * Alloy columns are 1-based and byte-ish (ASCII in practice), so we map straight to a
     * document offset. Range length defaults to "to end of line" — callers can narrow later
     * if we start parsing carets/underlines from stderr.
     */
    private fun rangeFor(doc: Document, d: AlloyValidatorOutputParser.Diagnostic): TextRange? {
        val line = d.line ?: return null
        val col = d.column ?: 1
        val lineIdx = (line - 1).coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
        val lineStart = doc.getLineStartOffset(lineIdx)
        val lineEnd = doc.getLineEndOffset(lineIdx)
        val start = (lineStart + (col - 1)).coerceIn(lineStart, lineEnd)
        return TextRange(start, lineEnd)
    }
}
