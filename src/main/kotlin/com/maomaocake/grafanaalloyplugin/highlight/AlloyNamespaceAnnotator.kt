package com.maomaocake.grafanaalloyplugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes

/**
 * Colors the full block-name (e.g. `prometheus.scrape`, `loki.source.file`) according to the
 * leading namespace identifier. Does nothing for namespaces we don't recognize — they keep the
 * default identifier color.
 */
class AlloyNamespaceAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AlloyBlockName) return

        val firstIdent = element.firstChild?.takeIf { it.node.elementType == AlloyElementTypes.IDENT } ?: return
        val key = AlloyColors.namespaceKey(firstIdent.text) ?: return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element.textRange)
            .textAttributes(key)
            .create()
    }
}
