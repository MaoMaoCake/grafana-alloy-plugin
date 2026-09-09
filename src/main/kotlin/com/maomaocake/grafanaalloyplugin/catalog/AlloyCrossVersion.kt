package com.maomaocake.grafanaalloyplugin.catalog

/**
 * Answers "which *other* bundled Alloy versions define this component / argument / nested block?"
 * so inspections can tell a genuine typo (unknown in every bundled version) apart from a
 * version-mismatch (valid, just not in the version this project selected) — and offer to switch.
 *
 * All data comes from the bundled catalogs (immutable, identical across projects), so results are
 * memoized application-wide via [AlloyCatalogService.loadCatalog]. Returned version lists are in
 * ascending semantic-version order and use the manifest's exact version tags.
 */
object AlloyCrossVersion {

    // component name -> bundled versions that define it. Cheap (names only); built once.
    private val componentIndex: Map<String, List<String>> by lazy { buildComponentIndex() }

    private fun bundledVersions(): List<String> = AlloyCatalogService.manifest().versionTags()

    private fun buildComponentIndex(): Map<String, List<String>> {
        val map = HashMap<String, MutableList<String>>()
        for (version in bundledVersions()) {
            for (component in AlloyCatalogService.loadCatalog(version).components) {
                map.getOrPut(component.name) { mutableListOf() }.add(version)
            }
        }
        return map.mapValues { (_, versions) -> versions.sortedBy(::semverKey) }
    }

    /** Bundled versions whose catalog defines a top-level component named [name] (ascending). */
    fun versionsWithComponent(name: String): List<String> = componentIndex[name].orEmpty()

    /**
     * Bundled versions whose [componentName] defines an argument [argName] at nested block [path]
     * (empty [path] = the component's own body). Ascending. Computed on demand — only ever called
     * when an argument is already flagged unknown in the active version.
     */
    fun versionsWithArg(componentName: String, path: List<String>, argName: String): List<String> =
        versionsAtPath(componentName, path) { args, _ -> args.any { it.name == argName } }

    /**
     * As [versionsWithArg] but for a nested block named [blockName]. Uses the dotted-aware
     * resolver rather than a flat name match, so a *written dotted* block like `stage.truncate`
     * — which the catalog stores nested (`stage > truncate`), never as a literal `stage.truncate`
     * — resolves correctly. This mirrors the annotator's own presence check
     * (`resolvePath(component, ctx.path + nestedName)`); a flat `blocks.any { it.name == ... }`
     * would silently miss every dotted `loki.process` stage.
     */
    fun versionsWithBlock(componentName: String, path: List<String>, blockName: String): List<String> =
        bundledVersions().filter { version ->
            val component = AlloyCatalogService.loadCatalog(version).byName()[componentName] ?: return@filter false
            AlloyCatalogLookup.resolvePath(component, path + blockName) != null
        }.sortedBy(::semverKey)

    private inline fun versionsAtPath(
        componentName: String,
        path: List<String>,
        predicate: (List<AlloyArg>, List<AlloyBlock>) -> Boolean,
    ): List<String> = bundledVersions().filter { version ->
        val component = AlloyCatalogService.loadCatalog(version).byName()[componentName] ?: return@filter false
        val resolved = AlloyCatalogLookup.resolvePath(component, path) ?: return@filter false
        predicate(resolved.first, resolved.second)
    }.sortedBy(::semverKey)

    private fun semverKey(v: String): AlloyVersions.SemVer =
        AlloyVersions.parse(v) ?: AlloyVersions.SemVer(0, 0, 0)
}
