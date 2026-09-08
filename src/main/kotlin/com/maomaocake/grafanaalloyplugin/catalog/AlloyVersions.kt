package com.maomaocake.grafanaalloyplugin.catalog

/**
 * Small, dependency-free helpers for parsing and comparing Alloy version tags (`vX.Y.Z`).
 *
 * Kept pure (no IntelliJ / IO) so it can be unit-tested on a plain JVM and reused by both the
 * auto-detect path (mapping an `alloy --version` string to the nearest bundled catalog) and the
 * manifest's "latest bundled" resolution.
 */
object AlloyVersions {

    /** `major.minor.patch`, ignoring any `v` prefix and pre-release / build suffixes. */
    data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int =
            compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

        override fun toString(): String = "v$major.$minor.$patch"
    }

    private val SEMVER = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

    /**
     * Extracts the first `X.Y.Z` (optionally `v`-prefixed) triple from an arbitrary string.
     * Tolerant on purpose: `alloy --version` prints something like
     * `alloy, version v1.9.2 (branch: ...)`, so we scan for the first version-shaped token
     * rather than expecting a clean tag. Returns null when nothing matches.
     */
    fun parse(raw: String?): SemVer? {
        if (raw.isNullOrBlank()) return null
        val m = SEMVER.find(raw) ?: return null
        return runCatching {
            SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }.getOrNull()
    }

    /**
     * Picks the bundled version closest to [detected]: the highest bundled version that is
     * `<= detected`, so we never surface arguments/components the user's actual (older) binary
     * doesn't have. When [detected] is older than every bundled version, falls back to the
     * lowest bundled one. Returns null only when [bundled] has no parseable versions.
     *
     * [bundled] entries are the raw version strings from the manifest; the returned value is one
     * of them verbatim (so it matches a catalog directory name).
     */
    fun nearestBundled(detected: SemVer, bundled: List<String>): String? {
        val parsed = bundled.mapNotNull { raw -> parse(raw)?.let { it to raw } }
            .sortedBy { it.first }
        if (parsed.isEmpty()) return null
        val atOrBelow = parsed.lastOrNull { it.first <= detected }
        return (atOrBelow ?: parsed.first()).second
    }

    /** The highest version in [bundled] by semantic order, or null if none parse. */
    fun latest(bundled: List<String>): String? =
        bundled.mapNotNull { raw -> parse(raw)?.let { it to raw } }
            .maxByOrNull { it.first }
            ?.second
}
