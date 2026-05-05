package com.maomaocake.grafanaalloyplugin.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.maomaocake.grafanaalloyplugin.AlloyFileType

object AlloyElementFactory {

    /**
     * Returns a freshly-parsed [AlloyBlockLabel] whose text is `"name"`. Used by rename /
     * setName flows to swap the old label node for a new one.
     *
     * Caveat: [name] is interpolated verbatim into the quoted literal. Callers must ensure it is
     * a valid Alloy identifier (per upstream `IsValidIdentifier`) — no quotes, backslashes, or
     * embedded newlines. We do not escape, because Alloy labels that can be referenced must
     * themselves be valid identifiers, which excludes anything needing escaping.
     */
    fun createBlockLabel(project: Project, name: String): AlloyBlockLabel {
        val src = "placeholder \"$name\" {}\n"
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.alloy", AlloyFileType, src)
        return PsiTreeUtil.findChildOfType(file, AlloyBlockLabel::class.java)
            ?: error("AlloyElementFactory: could not build block label from [$name]")
    }

    /**
     * Parses [chainText] (e.g. `a.b.c.d`) as an Alloy expression and returns the resulting
     * [AlloyOperExpr]. Returns null if parsing doesn't produce an oper-expr at top level —
     * callers should fall back to doing nothing rather than corrupting the tree.
     */
    fun createOperExprFromChain(project: Project, chainText: String): AlloyOperExpr? {
        val src = "__rename_target = $chainText\n"
        val file = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.alloy", AlloyFileType, src)
        return PsiTreeUtil.findChildOfType(file, AlloyOperExpr::class.java)
    }
}
