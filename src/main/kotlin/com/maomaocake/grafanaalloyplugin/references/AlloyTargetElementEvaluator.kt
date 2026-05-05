package com.maomaocake.grafanaalloyplugin.references

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes

/**
 * Lets the IDE find a rename/navigation target when the caret sits inside an Alloy block
 * label's STRING leaf.
 *
 * Why this is needed: the platform's caret-based target lookup walks upward from the leaf but
 * by default won't treat a string-literal leaf as belonging to a named declaration — so
 * right-clicking inside `"d"` in `prometheus.remote_write "d" { ... }` leaves Rename greyed out
 * even though the enclosing [AlloyBlockLabel] is a `PsiNameIdentifierOwner`. This evaluator
 * bridges that gap by promoting the STRING leaf to its [AlloyBlockLabel] parent for target
 * resolution.
 */
class AlloyTargetElementEvaluator : TargetElementEvaluatorEx2() {
    override fun getNamedElement(element: PsiElement): PsiElement? {
        if (element.node?.elementType === AlloyElementTypes.STRING) {
            val parent = element.parent
            if (parent is AlloyBlockLabel) return parent
        }
        return super.getNamedElement(element)
    }
}
