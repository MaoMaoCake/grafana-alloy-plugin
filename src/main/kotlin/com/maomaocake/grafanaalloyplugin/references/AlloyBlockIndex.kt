package com.maomaocake.grafanaalloyplugin.references

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.AlloyFileType
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlock
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile

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
     * All **top-level** [AlloyBlock]s reachable from [anchor] — the anchor's own top-level
     * blocks plus every top-level block in sibling `*.alloy` files in the same directory.
     *
     * Nested blocks (e.g. `endpoint { … }` inside `prometheus.remote_write { … }`) are
     * excluded so callers — reference resolvers, the annotator, port-type-aware completion —
     * only see declarations that could actually be the target of a dotted reference like
     * `prometheus.remote_write.rw.receiver`.
     */
    fun visibleBlocks(anchor: PsiFile): Sequence<AlloyBlock> = sequence {
        val seen = HashSet<VirtualFile>()
        val anchorVf = anchor.virtualFile
        if (anchorVf != null) seen += anchorVf
        yieldAll(topLevelBlocks(anchor))

        val dir = anchorVf?.parent ?: return@sequence
        val psiManager = PsiManager.getInstance(anchor.project)
        for (child in dir.children ?: emptyArray()) {
            if (child in seen) continue
            if (child.isDirectory) continue
            if (child.fileType !== AlloyFileType) continue
            val psi = psiManager.findFile(child) as? AlloyFile ?: continue
            seen += child
            yieldAll(topLevelBlocks(psi))
        }
    }

    private fun topLevelBlocks(file: PsiFile): List<AlloyBlock> =
        PsiTreeUtil.findChildrenOfType(file, AlloyBlock::class.java).filter { isTopLevel(it) }

    /** `file → statement → block`: the block's grandparent is the file. */
    private fun isTopLevel(block: AlloyBlock): Boolean =
        block.parent?.parent is com.intellij.psi.PsiFile
}
