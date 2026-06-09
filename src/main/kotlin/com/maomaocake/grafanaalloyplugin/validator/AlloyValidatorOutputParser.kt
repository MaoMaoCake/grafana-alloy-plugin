package com.maomaocake.grafanaalloyplugin.validator

/**
 * Parses the stderr of `alloy validate` into structured diagnostics.
 *
 * Observed format (Alloy v1.15, consistent with older minor releases):
 *
 *     Error: /abs/path/file.alloy:12:4: unrecognized attribute name "scrap_interval"
 *
 *     11 |   forward_to = [...]
 *     12 |   scrap_interval = "30s"
 *        |   ^^^^^^^^^^^^^^^^^^^^^^
 *     13 | }
 *
 *     Error: /abs/path/other.alloy:1:1: component "prometheus.write.queue" is at stability level...
 *     ...
 *
 *     Error: validation failed
 *     2026/05/08 16:24:06 collector server run finished with error: validation failed
 *
 * We grab lines that start with `Error: <path>:<line>:<col>: <message>`. Lines like
 * `Error: validation failed` (no location) are noise — we skip them. The snippet blocks
 * below each error get skipped too, since we already have the location.
 *
 * Anything we couldn't parse at all comes back as a single [Diagnostic] with no location
 * and the raw tail of stderr, so callers can surface the unparseable output in a banner
 * instead of silently dropping it.
 */
object AlloyValidatorOutputParser {

    /**
     * A single parsed diagnostic. [path] / [line] / [column] are null when the message
     * couldn't be mapped to a source location; callers then emit a file-level annotation.
     */
    data class Diagnostic(
        val path: String?,
        val line: Int?,    // 1-based
        val column: Int?,  // 1-based
        val message: String,
    )

    /**
     * Matches `Error: <path>:<line>:<col>: <message>`. The path swallows anything that
     * isn't a colon-followed-by-digit-colon — Alloy uses absolute file paths on Linux /
     * macOS so colons inside the path aren't a real worry.
     */
    private val LOCATED = Regex("""^Error:\s+(?<path>[^\n]+?):(?<line>\d+):(?<col>\d+):\s*(?<msg>.+)$""")

    fun parse(stderr: String): List<Diagnostic> {
        if (stderr.isBlank()) return emptyList()

        val out = mutableListOf<Diagnostic>()
        val unmatchedErrors = mutableListOf<String>()

        for (rawLine in stderr.lineSequence()) {
            val line = rawLine.trimEnd()
            if (line.isEmpty()) continue

            // Only lines that *start* with `Error:` could be a diagnostic. The rest are
            // snippet context, timestamps, or the tail-end "validation failed" summary.
            if (!line.startsWith("Error:")) continue

            // Skip the summary line `Error: validation failed` — it has no location and
            // adds no information beyond the fact that there was at least one other error.
            if (line.equals("Error: validation failed", ignoreCase = true)) continue

            val match = LOCATED.matchEntire(line)
            if (match != null) {
                out += Diagnostic(
                    path = match.groups["path"]!!.value,
                    line = match.groups["line"]!!.value.toIntOrNull(),
                    column = match.groups["col"]!!.value.toIntOrNull(),
                    message = match.groups["msg"]!!.value.trim(),
                )
            } else {
                // `Error: …` without a location. Keep for fallback display.
                unmatchedErrors += line.removePrefix("Error:").trim()
            }
        }

        // If we recognized at least one located diagnostic, drop the unmatched summaries —
        // they're almost always generic "validation failed" noise. Otherwise fall back to
        // reporting whatever we got so the user isn't staring at a silent editor.
        if (out.isNotEmpty()) return out
        return unmatchedErrors.map { Diagnostic(null, null, null, it) }
    }
}
