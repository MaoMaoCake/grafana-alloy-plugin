package com.maomaocake.grafanaalloyplugin.catalog

import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock as PsiBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Shared catalog-lookup helpers. Both the completion contributor and the inspections need to
 * ask "what schema applies at this PSI location?", so the walk lives here instead of being
 * duplicated.
 */
object AlloyCatalogLookup {

    /**
     * Schema that applies inside [block]'s body, if that block (or one of its ancestors) is a
     * known Alloy component. Returns null for:
     *   - blocks whose outermost ancestor isn't in the catalog (e.g. a block inside a
     *     user-defined `declare` module invocation),
     *   - blocks whose nested-block path doesn't resolve in the catalog.
     *
     * [BlockContext.component] is always the outermost Alloy component; [BlockContext.path] is
     * the list of nested block names from that component down to (and including) [block] when
     * it's nested, or empty when [block] is the component itself.
     */
    fun resolveBlock(block: PsiBlock): BlockContext? {
        val chain = mutableListOf<String>()
        var current: PsiBlock? = block
        while (current != null) {
            val name = AlloyPsiUtil.blockNameIdents(current.blockName).joinToString(".")
            chain.add(0, name)
            current = PsiTreeUtil.getParentOfType(current, PsiBlock::class.java, /* strict = */ true)
        }
        if (chain.isEmpty()) return null

        // `declare "mod" { ... }` defines a module: its body is a top-level scope. Strip a
        // leading `declare` so the component lookup begins one level in.
        val normalized = if (chain.firstOrNull() == "declare") chain.drop(1) else chain
        val rootName = normalized.firstOrNull() ?: return null

        val catalog = AlloyCatalogService.getInstance().catalog
        val component = catalog.byName()[rootName] ?: return null
        val path = normalized.drop(1)
        val (args, blocks) = resolvePath(component, path) ?: return null
        return BlockContext(component, path, args, blocks)
    }

    /**
     * Walks [component]'s nested blocks along [path] and returns the (args, blocks) available
     * at that depth. Returns null if the path doesn't resolve in the catalog.
     */
    fun resolvePath(
        component: AlloyComponent,
        path: List<String>,
    ): Pair<List<AlloyArg>, List<AlloyBlock>>? {
        if (path.isEmpty()) return component.argsList() to component.blocksList()
        var blocks = component.blocksList()
        var argsHere: List<AlloyArg> = emptyList()
        for (segment in path) {
            val match = blocks.firstOrNull { it.name == segment } ?: return null
            argsHere = match.argsList()
            blocks = match.blocksList()
        }
        return argsHere to blocks
    }

    data class BlockContext(
        val component: AlloyComponent,
        val path: List<String>,
        val args: List<AlloyArg>,
        val blocks: List<AlloyBlock>,
    )

    /**
     * Canonical key for a port type. Maps the various spellings used in the catalog's `goType`
     * column to the same identifier, so an arg's `[]X` and an export's `X` resolve to the
     * same key. Callers typically strip a leading `[]` before normalizing.
     */
    fun normalizePortType(goType: String): String? = when (goType) {
        "loki.LogsReceiver"    -> "LogsReceiver"
        "storage.Appendable"   -> "MetricsReceiver"
        "pyroscope.Appendable" -> "ProfilesReceiver"
        "otelcol.Consumer"     -> "OtelcolConsumer"
        "discovery.Target"     -> "Targets"
        else                   -> null
    }
}
