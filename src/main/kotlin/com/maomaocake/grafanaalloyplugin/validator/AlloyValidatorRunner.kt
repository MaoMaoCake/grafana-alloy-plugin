package com.maomaocake.grafanaalloyplugin.validator

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Invokes `alloy validate` against a directory and returns the captured output.
 *
 * Runs off-EDT (caller's responsibility). Uses [CapturingProcessHandler], which is the
 * standard IntelliJ idiom for "run a short-lived external tool and collect stderr/stdout".
 *
 * Detection caveats:
 *  - Windows callers must gate on [AlloyValidatorAvailability.isSupportedOs] and never
 *    reach here. We treat a Windows call as an error (empty result, warning logged) rather
 *    than shelling out and getting a confusing "unrecognized command" message.
 *  - An empty `binaryPath` means "look it up on PATH". Anything else is used verbatim.
 */
object AlloyValidatorRunner {

    private val LOG = Logger.getInstance(AlloyValidatorRunner::class.java)

    data class RunResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        /** Populated when we couldn't even start the process (binary missing, timeout, etc). */
        val failureReason: String? = null,
    ) {
        val crashedBeforeRunning: Boolean get() = failureReason != null
    }

    /**
     * Runs `alloy validate <target>` where [target] may be either a single `.alloy` file or
     * a directory of them. Returns the captured output; does not throw. [timeoutMs] caps
     * wall-clock time so a misbehaving binary can't hang the IDE.
     */
    fun run(
        project: Project,
        target: File,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): RunResult {
        if (!AlloyValidatorAvailability.isSupportedOs) {
            return RunResult(-1, "", "", "alloy validate is unavailable on this OS")
        }

        val settings = AlloyValidatorSettings.getInstance(project)
        val binary = settings.binaryPath.trim().ifEmpty { "alloy" }

        val cmd = GeneralCommandLine(binary, "validate").apply {
            // Alloy prints pretty output (ANSI colors, snippets) by default. In a pty we
            // usually don't want colors in captured text, so force a plain terminal.
            withEnvironment("NO_COLOR", "1")
            withEnvironment("TERM", "dumb")
            addParameter("--stability.level=${settings.stabilityLevel.flag}")
            if (settings.communityComponentsEnabled) {
                addParameter("--feature.community-components.enabled")
            }
            addParameter(target.absolutePath)
            workDirectory = if (target.isDirectory) target else target.parentFile
        }

        return try {
            val handler = CapturingProcessHandler(cmd)
            val output: ProcessOutput = handler.runProcess(timeoutMs.toInt(), /* destroyOnTimeout = */ true)
            if (output.isTimeout) {
                RunResult(-1, output.stdout, output.stderr, "alloy validate exceeded ${timeoutMs}ms timeout")
            } else {
                RunResult(output.exitCode, output.stdout, output.stderr)
            }
        } catch (t: Throwable) {
            // Binary missing, permission denied, etc. Log once and surface the message up.
            LOG.warn("Failed to run `$binary validate`", t)
            RunResult(-1, "", "", t.message ?: t::class.java.simpleName)
        }
    }

    /**
     * One-shot probe: runs `<binary> --version` and returns the first line, or null when
     * the binary isn't runnable. Used by the Settings page and the detector to tell users
     * whether their path works before they commit any configs.
     */
    fun probeVersion(binaryPath: String, timeoutMs: Long = 5_000L): String? {
        if (!AlloyValidatorAvailability.isSupportedOs) return null
        val binary = binaryPath.trim().ifEmpty { "alloy" }
        val cmd = GeneralCommandLine(binary, "--version")
        return try {
            val output = CapturingProcessHandler(cmd).runProcess(timeoutMs.toInt(), true)
            if (output.isTimeout || output.exitCode != 0) null
            else output.stdout.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private const val DEFAULT_TIMEOUT_MS = 20_000L

    // Unused, kept to document that we could opt into a pty for richer output if the
    // capture-based approach ever proves insufficient.
    @Suppress("unused") private val PTY_EXAMPLE = PtyCommandLine::class
}
