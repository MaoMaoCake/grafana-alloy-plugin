package com.maomaocake.grafanaalloyplugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogLookup
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCrossVersion
import com.maomaocake.grafanaalloyplugin.catalog.AlloyVersions
import com.maomaocake.grafanaalloyplugin.catalog.SwitchAlloyVersionFix
import com.maomaocake.grafanaalloyplugin.psi.AlloyAttribute
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Catalog-driven inspections. For each Alloy block whose component is in our catalog:
 *
 *  - **Unknown argument**: an attribute name that's not declared on the enclosing component
 *    or nested block. Warning-level, not error-level — our catalog can lag upstream, and we
 *    don't want red squiggles over a valid-but-new attribute.
 *  - **Unknown nested block**: same idea for block-style children.
 *  - **Missing required argument**: a component/block lacks an attribute the catalog marks
 *    required. Error-level because this genuinely won't pass `alloy validate`.
 *  - **Stability**: weak warning on components/blocks whose component is `public-preview`
 *    or `experimental`.
 *  - **Port-type mismatch**: a reference like `prometheus.remote_write.rw.receiver` inside a
 *    `forward_to` arg, but the arg is a logs receiver list. Warning-level.
 *
 * Walks run on the PSI elements the platform hands us one at a time — no whole-file scan per
 * element. Whole-block checks fire on `AlloyBlock` (once per block).
 */
class AlloyCatalogAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is AlloyBlock    -> annotateBlock(element, holder)
            is AlloyOperExpr -> annotatePortTypeMismatch(element, holder)
            else             -> Unit
        }
    }

    // -------------------------------------------------------------------------------------
    // Block-level: unknown/missing args + blocks, stability warning.
    // -------------------------------------------------------------------------------------

    private fun annotateBlock(block: AlloyBlock, holder: AnnotationHolder) {
        val ctx = AlloyCatalogLookup.resolveBlock(block)
        if (ctx == null) {
            // Not a known component in the *active* catalog. It may still be a real component that
            // only exists in another bundled Alloy version — flag that as a version mismatch and
            // offer to switch. (Genuinely-unknown names fall through silently, as before.)
            annotateComponentVersionMismatch(block, holder)
            return
        }

        // Stability: flag once per outermost component block. Suppress for nested blocks so we
        // don't spam the same warning on every sub-block.
        if (ctx.path.isEmpty()) {
            when (ctx.component.stability) {
                "experimental"   -> holder.newAnnotation(
                    HighlightSeverity.WEAK_WARNING,
                    "Alloy component `${ctx.component.name}` is experimental",
                ).range(block.blockName.textRange).create()
                "public-preview" -> holder.newAnnotation(
                    HighlightSeverity.WEAK_WARNING,
                    "Alloy component `${ctx.component.name}` is in public preview",
                ).range(block.blockName.textRange).create()
            }
        }

        val knownArgs = ctx.args.associateBy { it.name }
        val knownBlocks = ctx.blocks.associateBy { it.name }
        val childStatements = block.blockBody.children

        val presentArgs = mutableSetOf<String>()

        for (child in childStatements) {
            val attr = PsiTreeUtil.findChildOfType(child, AlloyAttribute::class.java, false)
            if (attr != null && attr.parent === child) {
                val nameLeaf = attr.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT } ?: continue
                val name = nameLeaf.text
                presentArgs += name
                if (name !in knownArgs) {
                    // If it matches a nested-block name instead, user probably meant a block —
                    // skip the unknown-arg warning; the block-shaped version will be flagged by
                    // the inner block annotation pass when they change syntax.
                    if (name in knownBlocks) continue
                    val otherVersions = AlloyCrossVersion.versionsWithArg(ctx.component.name, ctx.path, name)
                    if (otherVersions.isNotEmpty()) {
                        reportVersionMismatch(
                            holder, nameLeaf.textRange, block.project,
                            "Argument `$name` on `${ctx.component.name}${pathSuffix(ctx.path)}`",
                            otherVersions,
                        )
                    } else {
                        holder.newAnnotation(
                            HighlightSeverity.WARNING,
                            "Unknown argument `$name` on `${ctx.component.name}${pathSuffix(ctx.path)}`",
                        ).range(nameLeaf.textRange).create()
                    }
                }
                continue
            }
            val nested = PsiTreeUtil.findChildOfType(child, AlloyBlock::class.java, false)
            if (nested != null && nested.parent === child) {
                val nestedName = AlloyPsiUtil.blockNameIdents(nested.blockName).joinToString(".")
                val nestedBlockResolves =
                    AlloyCatalogLookup.resolvePath(ctx.component, ctx.path + nestedName) != null
                if (!nestedBlockResolves && nestedName !in knownArgs) {
                    val otherVersions = AlloyCrossVersion.versionsWithBlock(ctx.component.name, ctx.path, nestedName)
                    if (otherVersions.isNotEmpty()) {
                        reportVersionMismatch(
                            holder, nested.blockName.textRange, block.project,
                            "Nested block `$nestedName` in `${ctx.component.name}${pathSuffix(ctx.path)}`",
                            otherVersions,
                        )
                    } else {
                        holder.newAnnotation(
                            HighlightSeverity.WARNING,
                            "Unknown nested block `$nestedName` in `${ctx.component.name}${pathSuffix(ctx.path)}`",
                        ).range(nested.blockName.textRange).create()
                    }
                }
            }
        }

        // Missing required arguments: flag each missing one at the block name.
        val missingRequired = ctx.args.filter { it.required && it.name !in presentArgs }
        if (missingRequired.isNotEmpty()) {
            val names = missingRequired.joinToString(", ") { it.name }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Missing required argument${if (missingRequired.size == 1) "" else "s"}: $names",
            ).range(block.blockName.textRange).create()
        }
    }

    private fun pathSuffix(path: List<String>): String =
        if (path.isEmpty()) "" else " > " + path.joinToString(" > ")

    /**
     * A top-level block whose name didn't resolve in the active catalog. If the name is a real
     * component in another bundled version, flag a version mismatch and offer to switch; otherwise
     * stay quiet (unknown-everywhere names are handled elsewhere / tolerated, so the catalog can
     * lag upstream without red squiggles).
     */
    private fun annotateComponentVersionMismatch(block: AlloyBlock, holder: AnnotationHolder) {
        // Only the outermost block: a `declare` module body counts as top-level, but a block
        // nested inside another (unknown) block would just cascade the same error.
        val parentBlock = PsiTreeUtil.getParentOfType(block, AlloyBlock::class.java, /* strict = */ true)
        val parentIsDeclare = parentBlock != null &&
            AlloyPsiUtil.blockNameIdents(parentBlock.blockName).joinToString(".") == "declare"
        if (parentBlock != null && !parentIsDeclare) return

        val name = AlloyPsiUtil.blockNameIdents(block.blockName).joinToString(".")
        // Component names are always dotted (`namespace.component`); single-segment names are
        // `declare` modules or custom-module invocations — never catalog components.
        if ('.' !in name) return

        val available = AlloyCrossVersion.versionsWithComponent(name)
        if (available.isEmpty()) return

        reportVersionMismatch(holder, block.blockName.textRange, block.project, "Component `$name`", available)
    }

    /**
     * Emits an ERROR saying [subject] isn't in the project's selected catalog but is available in
     * [available], with a "switch to vX" quick-fix per candidate version (newest first). No-ops if
     * the only version that has it is the one already selected.
     */
    private fun reportVersionMismatch(
        holder: AnnotationHolder,
        range: TextRange,
        project: Project,
        subject: String,
        available: List<String>,
    ) {
        val active = AlloyCatalogService.getInstance(project).activeVersion
        val others = available.filter { it != active }
        if (others.isEmpty()) return

        val builder = holder.newAnnotation(
            HighlightSeverity.ERROR,
            "$subject is not available in the selected Alloy version" +
                (if (active.isNotBlank()) " ($active)" else "") +
                ". Available in: ${others.joinToString(", ")}.",
        ).range(range)
        for (version in others.sortedByDescending { AlloyVersions.parse(it) }) {
            builder.withFix(SwitchAlloyVersionFix(version))
        }
        builder.create()
    }

    // -------------------------------------------------------------------------------------
    // Reference-level: port-type mismatch.
    // -------------------------------------------------------------------------------------

    private fun annotatePortTypeMismatch(oper: AlloyOperExpr, holder: AnnotationHolder) {
        // Only act on references that actually resolve (i.e. point at a labeled block).
        val resolved = oper.references.firstNotNullOfOrNull { it.resolve() } ?: return
        val targetBlock = PsiTreeUtil.getParentOfType(resolved, AlloyBlock::class.java) ?: return
        val targetComponent = AlloyCatalogLookup.resolveBlock(targetBlock)?.component ?: return

        // Which attribute is this reference the value of? Walk up to the enclosing attribute.
        val attr = PsiTreeUtil.getParentOfType(oper, AlloyAttribute::class.java) ?: return
        val attrName = attr.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT }?.text ?: return

        // Find the attribute's declared goType by looking up the schema of its enclosing block.
        val enclosingBlock = PsiTreeUtil.getParentOfType(attr, AlloyBlock::class.java) ?: return
        val ctx = AlloyCatalogLookup.resolveBlock(enclosingBlock) ?: return
        val argSpec = ctx.args.firstOrNull { it.name == attrName } ?: return
        val acceptedPort = AlloyCatalogLookup.normalizePortType(argSpec.goType.removePrefix("[]")) ?: return

        val referencedExport = lastChainSegment(oper) ?: return
        val matchingExport = targetComponent.exportsList().firstOrNull { it.name == referencedExport } ?: return
        val providedPort = AlloyCatalogLookup.normalizePortType(matchingExport.goType.removePrefix("[]")) ?: return

        if (providedPort != acceptedPort) {
            // targetBlock.blockLabel is optional in general but non-null here: the resolved
            // reference comes from `blockLabel.parent`, so the enclosing block has one.
            val targetLabel = targetBlock.blockLabel?.let { AlloyPsiUtil.unquoteLabel(it) } ?: "?"
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                "Port-type mismatch: `$attrName` expects ${humanPort(acceptedPort)}, " +
                    "but `${targetComponent.name}.$targetLabel.$referencedExport` exports ${humanPort(providedPort)}",
            ).range(oper.textRange).create()
        }
    }

    private fun lastChainSegment(oper: AlloyOperExpr): String? {
        val chain = AlloyPsiUtil.identChain(oper) ?: return null
        return chain.lastOrNull()?.text
    }

    private fun humanPort(key: String): String = when (key) {
        "MetricsReceiver"  -> "Prometheus MetricsReceiver"
        "LogsReceiver"     -> "Loki LogsReceiver"
        "ProfilesReceiver" -> "Pyroscope ProfilesReceiver"
        "OtelcolConsumer"  -> "OpenTelemetry otelcol.Consumer"
        "Targets"          -> "Targets"
        else               -> key
    }
}
