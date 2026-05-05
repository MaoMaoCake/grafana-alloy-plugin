package com.maomaocake.grafanaalloyplugin.catalog

/**
 * In-memory component catalog. Populated once at plugin start from
 * `resources/alloy/components.json` (produced by the Go-side
 * `catalog-generator`).
 *
 * Data classes mirror the JSON shape exactly. Keep in lockstep with
 * `catalog-generator/main.go`.
 */
data class AlloyCatalog(
    val alloyVersion: String,
    val generatedAt: String,
    val components: List<AlloyComponent>,
) {
    val byName: Map<String, AlloyComponent> = components.associateBy { it.name }
    val byNamespace: Map<String, List<AlloyComponent>> = components.groupBy { it.namespace }

    companion object {
        val EMPTY = AlloyCatalog(
            alloyVersion = "unknown",
            generatedAt = "",
            components = emptyList(),
        )
    }
}

data class AlloyComponent(
    val name: String,
    val namespace: String,
    val stability: String,
    val community: Boolean,
    val docsUrl: String,
    val args: List<AlloyArg>? = null,
    val blocks: List<AlloyBlock>? = null,
    val exports: List<AlloyExport>? = null,
    val acceptedPortTypes: List<String>? = null,
    val exportedPortTypes: List<String>? = null,
) {
    // Nullable fields tolerate `null` from the Go-side JSON (where recursion bottoms out);
    // callers should use these non-null views instead.
    fun argsList(): List<AlloyArg> = args ?: emptyList()
    fun blocksList(): List<AlloyBlock> = blocks ?: emptyList()
    fun exportsList(): List<AlloyExport> = exports ?: emptyList()
    fun accepted(): List<String> = acceptedPortTypes ?: emptyList()
    fun exported(): List<String> = exportedPortTypes ?: emptyList()
}

data class AlloyArg(
    val name: String,
    val goType: String,
    val required: Boolean,
)

data class AlloyBlock(
    val name: String,
    val optional: Boolean,
    val repeated: Boolean,
    val label: Boolean,
    val args: List<AlloyArg>? = null,
    val blocks: List<AlloyBlock>? = null,
) {
    fun argsList(): List<AlloyArg> = args ?: emptyList()
    fun blocksList(): List<AlloyBlock> = blocks ?: emptyList()
}

data class AlloyExport(
    val name: String,
    val goType: String,
    val portType: String? = null,
)
