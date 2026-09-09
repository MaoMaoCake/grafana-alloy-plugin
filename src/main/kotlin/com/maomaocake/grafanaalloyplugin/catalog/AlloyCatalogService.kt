package com.maomaocake.grafanaalloyplugin.catalog

import com.google.gson.Gson
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.maomaocake.grafanaalloyplugin.validator.AlloyValidatorRunner
import com.maomaocake.grafanaalloyplugin.validator.AlloyValidatorSettings
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level service that hands out the *active* component catalog for this project.
 *
 * Which catalog is active depends on [AlloyCatalogSettings]:
 *   - `LatestBundled` → the manifest's default (newest bundled) version,
 *   - `Pinned` → an explicit version tag (falling back to latest bundled if it isn't bundled),
 *   - `AutoDetect` → the bundled catalog nearest the configured `alloy` binary's reported
 *     version, resolved lazily on a background thread (falls back to latest bundled meanwhile).
 *
 * Access via `AlloyCatalogService.getInstance(project).catalog`. Catalogs are parsed at most
 * once per version and cached; if a catalog fails to load we degrade to [AlloyCatalog.EMPTY] so
 * the editor keeps working (no completions/validation rather than a broken file).
 *
 * We use Gson rather than Jackson because Jackson's Kotlin module isn't on the IntelliJ runtime
 * classpath (test classpath only). Gson is always bundled and handles Kotlin data classes fine.
 */
@Service(Service.Level.PROJECT)
class AlloyCatalogService(private val project: Project) {

    // Result of the `alloy --version` probe, mapped to the nearest bundled version. Null until a
    // probe succeeds (or if none has yet). Once set, resolveVersion() short-circuits to it.
    @Volatile private var autoDetected: String? = null
    // Guards against overlapping probes, and throttles retries after a failed probe so we don't
    // shell out on every keystroke (resolveVersion runs per completion/highlight pass).
    private val probeInFlight = AtomicBoolean(false)
    @Volatile private var lastProbeStartedAt: Long = 0L

    /** The catalog for this project's currently-resolved Alloy version. */
    val catalog: AlloyCatalog
        get() = loadCatalog(activeVersion)

    /** The version tag currently in effect for this project (a manifest version, or "" if none). */
    val activeVersion: String
        get() = resolveVersion()

    /** The bundled-catalog index (shared across projects; version-independent). */
    val manifest: AlloyCatalogManifest
        get() = sharedManifest

    private fun resolveVersion(): String {
        val settings = AlloyCatalogSettings.getInstance(project)
        return when (settings.versionMode) {
            AlloyCatalogSettings.VersionMode.LatestBundled -> sharedManifest.resolvedDefault()

            AlloyCatalogSettings.VersionMode.Pinned ->
                settings.pinnedVersion.takeIf { sharedManifest.hasVersion(it) }
                    ?: sharedManifest.resolvedDefault()

            AlloyCatalogSettings.VersionMode.AutoDetect ->
                autoDetected ?: run {
                    maybeStartAutoDetect()
                    sharedManifest.resolvedDefault()
                }
        }
    }

    /**
     * Kicks off the version probe on a pooled thread (never the EDT — the probe shells out and
     * blocks). At most one probe runs at a time; after a *failed* probe we retry no more than
     * once per [PROBE_RETRY_THROTTLE_MS] so a transient failure (or a binary configured only
     * later) self-heals without spawning a shell-out on every keystroke. On success it records
     * the nearest bundled version and restarts the daemon so open files re-highlight against it.
     */
    private fun maybeStartAutoDetect() {
        if (autoDetected != null) return
        if (!probeInFlight.compareAndSet(false, true)) return
        val now = System.currentTimeMillis()
        if (now - lastProbeStartedAt < PROBE_RETRY_THROTTLE_MS) {
            probeInFlight.set(false)
            return
        }
        lastProbeStartedAt = now
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (project.isDisposed) return@executeOnPooledThread
                val binaryPath = AlloyValidatorSettings.getInstance(project).binaryPath
                val raw = AlloyValidatorRunner.probeVersion(binaryPath) ?: return@executeOnPooledThread
                val detectedSemver = AlloyVersions.parse(raw) ?: return@executeOnPooledThread
                val nearest = AlloyVersions.nearestBundled(detectedSemver, sharedManifest.versionTags())
                    ?: return@executeOnPooledThread
                if (nearest == autoDetected) return@executeOnPooledThread
                autoDetected = nearest
                LOG.info("Auto-detected Alloy $raw -> nearest bundled catalog $nearest")
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart()
                }
            } finally {
                probeInFlight.set(false)
            }
        }
    }

    /**
     * Drops cached state so the next [catalog] / [activeVersion] access recomputes from current
     * settings and re-runs auto-detect immediately. Call after a settings change (including the
     * `alloy` binary path on the Validate page); pair it with a [DaemonCodeAnalyzer.restart] so
     * open files pick up the new catalog. Parsed catalogs stay cached (keyed by version) — they
     * don't change within a session.
     */
    fun reload() {
        autoDetected = null
        lastProbeStartedAt = 0L
    }

    companion object {
        private val LOG = Logger.getInstance(AlloyCatalogService::class.java)

        // Minimum spacing between auto-detect probe attempts after a failure (ms).
        private const val PROBE_RETRY_THROTTLE_MS = 60_000L

        // Manifest is identical across projects, so load it once for the whole application.
        private val sharedManifest: AlloyCatalogManifest by lazy { AlloyCatalogManifest.loadBundled() }

        // version tag -> parsed catalog. Catalogs are immutable and identical across projects, so
        // this is cached application-wide (the cross-version index and every project share it).
        private val catalogCache = ConcurrentHashMap<String, AlloyCatalog>()

        fun getInstance(project: Project): AlloyCatalogService =
            project.getService(AlloyCatalogService::class.java)

        /** The bundled-catalog index (shared across projects; version-independent). */
        fun manifest(): AlloyCatalogManifest = sharedManifest

        /**
         * Loads and memoizes a bundled catalog by version tag. Returns [AlloyCatalog.EMPTY] for a
         * blank/absent/corrupt catalog so callers degrade gracefully rather than crashing.
         */
        fun loadCatalog(version: String): AlloyCatalog {
            if (version.isBlank()) return AlloyCatalog.EMPTY
            return catalogCache.getOrPut(version) { parseCatalog(version) }
        }

        private fun parseCatalog(version: String): AlloyCatalog {
            val path = "/alloy/catalogs/$version/components.json"
            val stream = AlloyCatalogService::class.java.getResourceAsStream(path)
            if (stream == null) {
                LOG.warn("Alloy catalog resource $path not found — completions and validation disabled for $version")
                return AlloyCatalog.EMPTY
            }
            return try {
                stream.use { raw ->
                    InputStreamReader(raw, StandardCharsets.UTF_8).use { reader ->
                        Gson().fromJson(reader, AlloyCatalog::class.java) ?: AlloyCatalog.EMPTY
                    }
                }.also { LOG.info("Loaded Alloy catalog v=${it.alloyVersion} components=${it.components.size}") }
            } catch (t: Throwable) {
                LOG.warn("Failed to parse Alloy catalog at $path", t)
                AlloyCatalog.EMPTY
            }
        }
    }
}
