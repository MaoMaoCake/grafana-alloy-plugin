package com.maomaocake.grafanaalloyplugin.catalog

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Index of the catalogs bundled in the plugin jar. Loaded once from
 * `resources/alloy/catalogs/manifest.json`, which is produced by
 * `catalog-generator/build-catalog.sh` (regenerated from whatever catalog directories exist on
 * disk, so it can never list a version whose `components.json` is missing).
 *
 * Data classes mirror the JSON shape exactly (Gson populates via reflection — no init/lazy
 * state, see [AlloyCatalog]). Keep in lockstep with the `regen_manifest` step in
 * `build-catalog.sh`.
 */
data class AlloyCatalogManifest(
    val defaultVersion: String,
    val versions: List<Entry>,
) {
    data class Entry(
        val version: String,
        val components: Int,
        val generatedAt: String,
    )

    /** Raw version tags in the order the manifest lists them (ascending by semver). */
    fun versionTags(): List<String> = versions.map { it.version }

    fun hasVersion(version: String): Boolean = versions.any { it.version == version }

    fun entry(version: String): Entry? = versions.firstOrNull { it.version == version }

    /**
     * The version to use when the user hasn't pinned one. Prefers the manifest's declared
     * [defaultVersion] when it's actually present, else the latest bundled, else "" (empty
     * manifest → catalog service degrades to [AlloyCatalog.EMPTY]).
     */
    fun resolvedDefault(): String = when {
        hasVersion(defaultVersion) -> defaultVersion
        else -> AlloyVersions.latest(versionTags()) ?: versionTags().lastOrNull() ?: ""
    }

    companion object {
        private val LOG = Logger.getInstance(AlloyCatalogManifest::class.java)
        const val MANIFEST_PATH = "/alloy/catalogs/manifest.json"

        val EMPTY = AlloyCatalogManifest(defaultVersion = "", versions = emptyList())

        /** Loads the bundled manifest, degrading to [EMPTY] on any failure. */
        fun loadBundled(): AlloyCatalogManifest {
            val stream = AlloyCatalogManifest::class.java.getResourceAsStream(MANIFEST_PATH)
            if (stream == null) {
                LOG.warn("Alloy catalog manifest $MANIFEST_PATH not found — no catalogs will load")
                return EMPTY
            }
            return try {
                stream.use { raw ->
                    InputStreamReader(raw, StandardCharsets.UTF_8).use { reader ->
                        Gson().fromJson(reader, AlloyCatalogManifest::class.java) ?: EMPTY
                    }
                }.also { LOG.info("Loaded Alloy catalog manifest: default=${it.defaultVersion} versions=${it.versionTags()}") }
            } catch (t: Throwable) {
                LOG.warn("Failed to parse Alloy catalog manifest at $MANIFEST_PATH", t)
                EMPTY
            }
        }
    }
}
