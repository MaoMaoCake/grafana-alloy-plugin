package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Persistent console for `alloy validate` output, hosted in the Run tool window. Each
 * parsed diagnostic is printed as a `path:line:col` hyperlink that jumps the editor to
 * the offending location; the raw stdout/stderr is appended below so users can see the
 * snippet context (carets, surrounding lines) the parser strips out.
 *
 * Reuses a single [RunContentDescriptor] per project — invocations after the first replace
 * the previous output rather than spawning new tabs.
 */
@Service(Service.Level.PROJECT)
class AlloyValidateConsole(private val project: Project) {

    private var descriptor: RunContentDescriptor? = null

    fun show(
        targetName: String,
        result: AlloyValidatorRunner.RunResult,
        diagnostics: List<AlloyValidatorOutputParser.Diagnostic>,
    ) {
        ApplicationManager.getApplication().invokeLater {
            val (console, desc) = ensureConsole()
            console.clear()
            console.print("$ alloy validate $targetName\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            if (result.crashedBeforeRunning) {
                console.print("\nFailed to start: ${result.failureReason}\n", ConsoleViewContentType.ERROR_OUTPUT)
                RunContentManager.getInstance(project)
                    .showRunContent(DefaultRunExecutor.getRunExecutorInstance(), desc)
                return@invokeLater
            }

            console.print("exit ${result.exitCode}\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

            if (diagnostics.isNotEmpty()) {
                console.print("Issues (${diagnostics.size}):\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                for (d in diagnostics) {
                    printDiagnostic(console, d)
                }
            } else if (result.exitCode == 0) {
                console.print("No issues found.\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }

            if (result.stderr.isNotBlank()) {
                console.print("\n--- alloy stderr ---\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                console.print(result.stderr, ConsoleViewContentType.NORMAL_OUTPUT)
                if (!result.stderr.endsWith("\n")) console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }
            if (result.stdout.isNotBlank()) {
                console.print("\n--- alloy stdout ---\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                console.print(result.stdout, ConsoleViewContentType.NORMAL_OUTPUT)
            }

            RunContentManager.getInstance(project)
                .showRunContent(DefaultRunExecutor.getRunExecutorInstance(), desc)
        }
    }

    /**
     * Format: `<path>:<line>:<col>: <message>` with the location half rendered as a
     * hyperlink when we can resolve it back to a real `VirtualFile`. Falls back to plain
     * error text when the diagnostic was unlocated (parser couldn't extract path/line) or
     * when the path doesn't exist on disk anymore.
     */
    private fun printDiagnostic(console: ConsoleView, d: AlloyValidatorOutputParser.Diagnostic) {
        val locText = "${d.path ?: "<unknown>"}:${d.line ?: "?"}:${d.column ?: "?"}"
        val vf = d.path?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        if (vf != null && d.line != null) {
            console.printHyperlink(
                locText,
                OpenFileHyperlinkInfo(project, vf, d.line - 1, ((d.column ?: 1) - 1)),
            )
        } else {
            console.print(locText, ConsoleViewContentType.ERROR_OUTPUT)
        }
        console.print(": ${d.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
    }

    private fun ensureConsole(): Pair<ConsoleView, RunContentDescriptor> {
        val existing = descriptor
        if (existing != null && !Disposer.isDisposed(existing)) {
            val view = existing.executionConsole as? ConsoleView
            if (view != null) return view to existing
        }
        val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val handler = NopProcessHandler()
        console.attachToProcess(handler)
        handler.startNotify()
        val desc = RunContentDescriptor(console, handler, console.component, "Alloy Validate")
        Disposer.register(project, desc)
        descriptor = desc
        return console to desc
    }

    companion object {
        fun getInstance(project: Project): AlloyValidateConsole =
            project.getService(AlloyValidateConsole::class.java)
    }
}
