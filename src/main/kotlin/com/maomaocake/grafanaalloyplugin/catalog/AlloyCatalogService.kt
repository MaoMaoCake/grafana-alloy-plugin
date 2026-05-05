package com.maomaocake.grafanaalloyplugin.catalog

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Application-level service that loads the bundled component catalog once and hands it out.
 *
 * Access via `AlloyCatalogService.getInstance().catalog`. The catalog is immutable after load;
 * if loading fails we degrade to [AlloyCatalog.EMPTY] and log a warning — the plugin's
 * language features should tolerate this (no completions/validation, rather than breaking the
 * editor).
 *
 * We use Gson rather than Jackson because Jackson's Kotlin module isn't on the IntelliJ
 * runtime classpath (it's on the test classpath only). Gson is always bundled and handles
 * Kotlin data classes fine via reflection.
 */
@Service(Service.Level.APP)
class AlloyCatalogService {

    val catalog: AlloyCatalog = loadBundled()

    companion object {
        private val LOG = Logger.getInstance(AlloyCatalogService::class.java)
        private const val RESOURCE_PATH = "/alloy/components.json"

        fun getInstance(): AlloyCatalogService =
            ApplicationManager.getApplication().getService(AlloyCatalogService::class.java)

        private fun loadBundled(): AlloyCatalog {
            val stream = AlloyCatalogService::class.java.getResourceAsStream(RESOURCE_PATH)
            if (stream == null) {
                LOG.warn("Alloy catalog resource $RESOURCE_PATH not found — completions and validation will be disabled")
                return AlloyCatalog.EMPTY
            }
            return try {
                stream.use { raw ->
                    InputStreamReader(raw, StandardCharsets.UTF_8).use { reader ->
                        Gson().fromJson(reader, AlloyCatalog::class.java) ?: AlloyCatalog.EMPTY
                    }
                }.also { LOG.info("Loaded Alloy catalog v=${it.alloyVersion} components=${it.components.size}") }
            } catch (t: Throwable) {
                LOG.warn("Failed to parse Alloy catalog at $RESOURCE_PATH", t)
                AlloyCatalog.EMPTY
            }
        }
    }
}
