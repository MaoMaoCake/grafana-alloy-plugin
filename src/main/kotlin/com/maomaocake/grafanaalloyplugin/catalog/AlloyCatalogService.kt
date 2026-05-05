package com.maomaocake.grafanaalloyplugin.catalog

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

/**
 * Application-level service that loads the bundled component catalog once and
 * hands it out to callers.
 *
 * Access via `AlloyCatalogService.getInstance().catalog`. The catalog is
 * immutable after load; if loading fails (corrupt JSON, missing resource) we
 * degrade to [AlloyCatalog.EMPTY] and log — the plugin's language features
 * should tolerate this (no completions/validation, rather than breaking the
 * editor).
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
                stream.use {
                    val mapper = ObjectMapper().apply {
                        registerKotlinModule()
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                    mapper.readValue(it, AlloyCatalog::class.java)
                }.also { LOG.info("Loaded Alloy catalog v=${it.alloyVersion} components=${it.components.size}") }
            } catch (t: Throwable) {
                // Degraded mode: log once, return EMPTY. Language features that depend on the
                // catalog simply show no suggestions — we don't want a bad catalog to break the
                // editor.
                LOG.warn("Failed to parse Alloy catalog at $RESOURCE_PATH", t)
                AlloyCatalog.EMPTY
            }
        }
    }
}
