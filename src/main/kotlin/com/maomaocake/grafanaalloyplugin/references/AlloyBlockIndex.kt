package com.maomaocake.grafanaalloyplugin.references

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.AlloyFileType
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockBody
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil

/**
 * Enumerates every Alloy block visible from a given anchor file — the anchor's own blocks plus
 * every block in sibling `*.alloy` files in the same directory.
 *
 * Why "same directory": `alloy validate <dir>` treats a directory as a single unit, which maps
 * cleanly onto users splitting one configuration across a handful of files in the same folder.
 * Cross-directory references aren't something real Alloy configs use; we deliberately stop
 * the scope at directory boundaries to keep resolution fast and predictable.
 *
 * No caching on first pass — for a typical ~20 files × ~20 blocks this is trivially cheap.
 * If that changes, [CachedValuesManager] keyed on the directory's modification timestamp is
 * the drop-in upgrade.
 */
object AlloyBlockIndex {

    /**
     * All [AlloyBlock]s reachable from [anchor] — the anchor's own blocks plus every block in
     * sibling `*.alloy` files in the same directory.
     *
     * We yield nested blocks too (e.g. `endpoint { … }` inside `prometheus.remote_write`).
     * Callers all go on to compare against the component catalog by name, so nested-block
     * names like `endpoint` are filtered out downstream. Restricting here instead turned out
     * to interact badly with GrammarKit error recovery: a missing `}` earlier in a file can
     * re-parent a later block under an implicit wrapper, making `isTopLevel` return false
     * for a block the user clearly wrote at the top level — and the reference then shows up
     * as unresolved even though it should resolve fine. So we err on the side of yielding
     * too much and let callers filter.
     */
    fun visibleBlocks(anchor: PsiFile): Sequence<AlloyBlock> = sequence {
        val seen = HashSet<VirtualFile>()
        val anchorVf = anchor.virtualFile
        if (anchorVf != null) seen += anchorVf
        yieldAll(PsiTreeUtil.findChildrenOfType(anchor, AlloyBlock::class.java))

        val dir = anchorVf?.parent ?: return@sequence
        val psiManager = PsiManager.getInstance(anchor.project)
        for (child in dir.children ?: emptyArray()) {
            if (child in seen) continue
            if (child.isDirectory) continue
            if (child.fileType !== AlloyFileType) continue
            val psi = psiManager.findFile(child) as? AlloyFile ?: continue
            seen += child
            yieldAll(PsiTreeUtil.findChildrenOfType(psi, AlloyBlock::class.java))
        }
    }

    /**
     * Blocks visible from [origin] subject to Alloy's `declare` scoping:
     *   - If [origin] sits inside a `declare "X" { … }` body, the only visible blocks are the
     *     ones *also inside that declare's body*. A reference inside a module can't see
     *     blocks from the enclosing file or from other declares.
     *   - Otherwise (top-level, or inside a regular component body), the visible set is every
     *     block in [AlloyBlockIndex.visibleBlocks] *minus* those nested inside any declare
     *     body — modules don't publish their internal blocks to callers.
     *
     * This is the scoping rule callers (reference resolvers, reference-completion) should
     * prefer over the raw [visibleBlocks] — which stays available for cases that genuinely
     * want every block regardless of module boundaries.
     */
    fun visibleBlocksFrom(origin: PsiElement): Sequence<AlloyBlock> {
        val file = origin.containingFile ?: return emptySequence()
        val enclosingDeclare = enclosingDeclareBody(origin)
        return if (enclosingDeclare != null) {
            // Inside a declare → only its own body. Module bodies are self-contained.
            PsiTreeUtil.findChildrenOfType(enclosingDeclare, AlloyBlock::class.java).asSequence()
        } else {
            visibleBlocks(file).filter { !isInsideDeclareBody(it) }
        }
    }

    /** Returns the body of the nearest enclosing `declare "…" { … }` block, or null. */
    private fun enclosingDeclareBody(element: PsiElement): AlloyBlockBody? {
        var body = PsiTreeUtil.getParentOfType(element, AlloyBlockBody::class.java)
        while (body != null) {
            val block = body.parent as? AlloyBlock
            if (block != null && AlloyPsiUtil.blockNameIdents(block.blockName).singleOrNull() == "declare") {
                return body
            }
            body = PsiTreeUtil.getParentOfType(body, AlloyBlockBody::class.java, /* strict = */ true)
        }
        return null
    }

    /** True when [block] lives inside (at any depth under) some `declare "…" { … }`. */
    private fun isInsideDeclareBody(block: AlloyBlock): Boolean {
        var ancestorBody = PsiTreeUtil.getParentOfType(block, AlloyBlockBody::class.java)
        while (ancestorBody != null) {
            val owner = ancestorBody.parent as? AlloyBlock
            if (owner != null && AlloyPsiUtil.blockNameIdents(owner.blockName).singleOrNull() == "declare") {
                return true
            }
            ancestorBody = PsiTreeUtil.getParentOfType(ancestorBody, AlloyBlockBody::class.java, /* strict = */ true)
        }
        return false
    }
}
