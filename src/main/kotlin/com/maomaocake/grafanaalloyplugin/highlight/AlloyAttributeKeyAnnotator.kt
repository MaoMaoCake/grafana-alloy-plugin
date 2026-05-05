package com.maomaocake.grafanaalloyplugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.psi.AlloyAttribute
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyFieldKey

/**
 * Paints attribute and object-literal keys (`targets = …`, `{ url = "…" }`) with the
 * [AlloyColors.ATTRIBUTE_KEY] attributes so they stand out from reference identifiers.
 */
class AlloyAttributeKeyAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val ident = when (element) {
            is AlloyAttribute -> element.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT }
            is AlloyFieldKey  -> element.firstChild?.takeIf { it.node.elementType === AlloyElementTypes.IDENT }
            else -> null
        } ?: return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(ident.textRange)
            .textAttributes(AlloyColors.ATTRIBUTE_KEY)
            .create()
    }
}
