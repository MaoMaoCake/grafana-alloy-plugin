package com.maomaocake.grafanaalloyplugin.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.catalog.AlloyArg
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogLookup
import com.maomaocake.grafanaalloyplugin.catalog.AlloyCatalogService
import com.maomaocake.grafanaalloyplugin.catalog.AlloyComponent
import com.maomaocake.grafanaalloyplugin.psi.AlloyAttribute
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyOperExpr
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Quick-docs (Ctrl/Cmd-Q, hover) for Alloy code. Pulls all content from the bundled catalog.
 * Three kinds of targets we care about:
 *  - **Block name** (`prometheus.scrape` in `prometheus.scrape "s" { ... }`) — component-level
 *    metadata: stability, port types, docs URL.
 *  - **Attribute key** (`targets` in `targets = [...]`) — arg-level: Go type, required flag,
 *    the component it belongs to.
 *  - **Dotted reference** (`prometheus.remote_write.rw.receiver`) — resolved-block + export's
 *    port type.
 */
class AlloyDocumentationProvider : AbstractDocumentationProvider() {

    /** The main quick-docs popup. */
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = (element ?: originalElement) ?: return null
        return docForComponent(target)
            ?: docForAttribute(target)
            ?: docForReference(target)
    }

    /** Short hover tooltip (single line by convention). */
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: return null
        return quickLineForComponent(target)
            ?: quickLineForAttribute(target)
            ?: quickLineForReference(target)
    }

    /**
     * Platform calls this to turn a caret position into a "documentation target". We only
     * need to handle places where the target isn't the caret's own PSI — notably, navigation
     * from inside a [AlloyBlockName] up to the [AlloyBlock].
     */
    override fun getCustomDocumentationElement(
        editor: com.intellij.openapi.editor.Editor,
        file: com.intellij.psi.PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? {
        val el = contextElement ?: return null
        // IDENT under a block name → promote to the block so the component doc appears.
        val blockName = PsiTreeUtil.getParentOfType(el, AlloyBlockName::class.java)
        if (blockName != null) return blockName.parent as? AlloyBlock
        // IDENT that's an attribute key → attribute itself.
        val attr = PsiTreeUtil.getParentOfType(el, AlloyAttribute::class.java)
        if (attr != null) {
            val keyIdent = attr.firstChild
            if (keyIdent != null && PsiTreeUtil.isAncestor(keyIdent, el, /* strict = */ false)) return attr
        }
        // IDENT inside a dotted reference → the oper expression.
        val oper = PsiTreeUtil.getParentOfType(el, AlloyOperExpr::class.java)
        if (oper != null && AlloyPsiUtil.identChain(oper) != null) return oper
        return null
    }

    // -------------------------------------------------------------------------------------
    // Component documentation
    // -------------------------------------------------------------------------------------

    private fun docForComponent(target: PsiElement): String? {
        val block = when (target) {
            is AlloyBlock -> target
            is AlloyBlockName -> target.parent as? AlloyBlock
            else -> null
        } ?: return null
        val ctx = AlloyCatalogLookup.resolveBlock(block) ?: return null
        val component = ctx.component

        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>").append(component.name).append("</b>")
            if (ctx.path.isNotEmpty()) append(" ").append(DocumentationMarkup.GRAYED_ELEMENT.addText(" > " + ctx.path.joinToString(" > ")))
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)
            append("Alloy component in the <code>").append(component.namespace).append(".*</code> namespace.")
            append(DocumentationMarkup.CONTENT_END)

            append(DocumentationMarkup.SECTIONS_START)
            section("Stability", stabilityBadge(component))
            if (component.community) section("Community", "community component")
            component.accepted().takeIf { it.isNotEmpty() }?.let {
                section("Accepts", it.joinToString(", "))
            }
            component.exported().takeIf { it.isNotEmpty() }?.let {
                section("Exports", it.joinToString(", "))
            }
            // If we are docuumenting a nested block rather than the component itself, show the
            // schema for that nested block only.
            if (ctx.path.isNotEmpty()) {
                if (ctx.args.isNotEmpty()) section("Arguments", renderArgsTable(ctx.args))
                if (ctx.blocks.isNotEmpty()) section("Blocks", ctx.blocks.joinToString(", ") {
                    it.name + (if (it.repeated) "*" else "") + (if (it.optional) "?" else "")
                })
            } else {
                val rootArgs = component.argsList()
                if (rootArgs.isNotEmpty()) section("Arguments", renderArgsTable(rootArgs))
                val rootBlocks = component.blocksList()
                if (rootBlocks.isNotEmpty()) section("Blocks", rootBlocks.joinToString(", ") {
                    it.name + (if (it.repeated) "*" else "") + (if (it.optional) "?" else "")
                })
            }
            section("Docs", "<a href=\"${component.docsUrl}\">${component.docsUrl}</a>")
            append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun quickLineForComponent(target: PsiElement): String? {
        val block = target as? AlloyBlock ?: (target as? AlloyBlockName)?.parent as? AlloyBlock ?: return null
        val ctx = AlloyCatalogLookup.resolveBlock(block) ?: return null
        return "${ctx.component.name} — ${stabilityBadgePlain(ctx.component)}"
    }

    // -------------------------------------------------------------------------------------
    // Attribute documentation
    // -------------------------------------------------------------------------------------

    private fun docForAttribute(target: PsiElement): String? {
        val attr = target as? AlloyAttribute ?: return null
        val keyIdent = attr.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT } ?: return null
        val keyName = keyIdent.text
        val enclosing = PsiTreeUtil.getParentOfType(attr, AlloyBlock::class.java) ?: return null
        val ctx = AlloyCatalogLookup.resolveBlock(enclosing) ?: return null
        val arg = ctx.args.firstOrNull { it.name == keyName } ?: return null
        val scope = ctx.component.name + (if (ctx.path.isNotEmpty()) " > " + ctx.path.joinToString(" > ") else "")

        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>").append(arg.name).append("</b>: <code>").append(arg.goType).append("</code>")
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)
            append("Argument of <code>").append(scope).append("</code>.")
            append(DocumentationMarkup.CONTENT_END)

            append(DocumentationMarkup.SECTIONS_START)
            section("Required", if (arg.required) "yes" else "no")
            section("Type", "<code>${arg.goType}</code>")
            append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun quickLineForAttribute(target: PsiElement): String? {
        val attr = target as? AlloyAttribute ?: return null
        val keyName = attr.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT }?.text ?: return null
        val enclosing = PsiTreeUtil.getParentOfType(attr, AlloyBlock::class.java) ?: return null
        val ctx = AlloyCatalogLookup.resolveBlock(enclosing) ?: return null
        val arg = ctx.args.firstOrNull { it.name == keyName } ?: return null
        return "$keyName : ${arg.goType}${if (arg.required) " (required)" else ""}"
    }

    // -------------------------------------------------------------------------------------
    // Reference documentation
    // -------------------------------------------------------------------------------------

    private fun docForReference(target: PsiElement): String? {
        val oper = target as? AlloyOperExpr ?: return null
        val resolvedLabel = oper.references.firstNotNullOfOrNull { it.resolve() } as? AlloyBlockLabel ?: return null
        val targetBlock = PsiTreeUtil.getParentOfType(resolvedLabel, AlloyBlock::class.java) ?: return null
        val targetCtx = AlloyCatalogLookup.resolveBlock(targetBlock) ?: return null
        val chain = AlloyPsiUtil.identChain(oper) ?: return null
        val exportName = chain.lastOrNull()?.text
        val export = targetCtx.component.exportsList().firstOrNull { it.name == exportName }

        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>").append(chain.joinToString(".") { it.text }).append("</b>")
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)
            append("Reference to <code>").append(targetCtx.component.name).append(" \"")
                .append(AlloyPsiUtil.unquoteLabel(resolvedLabel) ?: "").append("\"</code>")
            if (export != null) {
                append(" · exports <code>").append(export.goType).append("</code>")
            }
            append(DocumentationMarkup.CONTENT_END)

            append(DocumentationMarkup.SECTIONS_START)
            targetCtx.component.exported().takeIf { it.isNotEmpty() }?.let {
                section("Exports", it.joinToString(", "))
            }
            section("Docs", "<a href=\"${targetCtx.component.docsUrl}\">${targetCtx.component.docsUrl}</a>")
            append(DocumentationMarkup.SECTIONS_END)
        }
    }

    private fun quickLineForReference(target: PsiElement): String? {
        val oper = target as? AlloyOperExpr ?: return null
        val resolvedLabel = oper.references.firstNotNullOfOrNull { it.resolve() } as? AlloyBlockLabel ?: return null
        val targetBlock = PsiTreeUtil.getParentOfType(resolvedLabel, AlloyBlock::class.java) ?: return null
        val targetCtx = AlloyCatalogLookup.resolveBlock(targetBlock) ?: return null
        val chain = AlloyPsiUtil.identChain(oper) ?: return null
        val exportName = chain.lastOrNull()?.text
        val portType = targetCtx.component.exportsList().firstOrNull { it.name == exportName }?.goType
        return "-> ${targetCtx.component.name} \"${AlloyPsiUtil.unquoteLabel(resolvedLabel) ?: ""}\"" +
            (if (portType != null) " · $portType" else "")
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private fun StringBuilder.section(name: String, body: String) {
        append(DocumentationMarkup.SECTION_HEADER_START)
        append(name).append(':')
        append(DocumentationMarkup.SECTION_SEPARATOR)
        append("<p>").append(body).append("</p>")
        append(DocumentationMarkup.SECTION_END)
    }

    private fun stabilityBadge(c: AlloyComponent): String = when (c.stability) {
        "generally-available" -> "GA"
        "public-preview"      -> "<b>public preview</b>"
        "experimental"        -> "<b>experimental</b>"
        else                  -> c.stability
    }

    private fun stabilityBadgePlain(c: AlloyComponent): String = when (c.stability) {
        "generally-available" -> "GA"
        else                  -> c.stability
    }

    private fun renderArgsTable(args: List<AlloyArg>): String {
        if (args.isEmpty()) return "none"
        val rows = args.take(20).joinToString("<br>") { a ->
            val req = if (a.required) "<b>*</b> " else "&nbsp;&nbsp;"
            "$req<code>${a.name}</code> : <code>${a.goType}</code>"
        }
        val suffix = if (args.size > 20) "<br>…${args.size - 20} more" else ""
        return rows + suffix
    }
}
