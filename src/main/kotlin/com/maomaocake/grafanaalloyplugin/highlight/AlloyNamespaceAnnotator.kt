package com.maomaocake.grafanaalloyplugin.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockName
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import java.awt.Color
import java.awt.Font

/**
 * Colors each segment of a dotted block name independently.
 *
 *  - Segment 0 (namespace) uses the registered [AlloyColors] namespace key, so users can
 *    customize it in the color scheme editor.
 *  - Segments 1+ get a stable color derived from the cumulative dotted prefix — e.g. every
 *    `loki.source.*` block shares one subgroup color, and every `loki.source.journal` leaf
 *    shares another. New or custom components get a reasonable color automatically.
 */
class AlloyNamespaceAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is AlloyBlockName) return

        val identNodes = element.node.getChildren(TokenSet.create(AlloyElementTypes.IDENT))
        if (identNodes.isEmpty()) return

        AlloyColors.namespaceKey(identNodes[0].text)?.let { key ->
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(identNodes[0].textRange)
                .textAttributes(key)
                .create()
        }

        val dark = isDarkEditor()
        var prefix = identNodes[0].text
        for (i in 1 until identNodes.size) {
            prefix = "$prefix.${identNodes[i].text}"
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(identNodes[i].textRange)
                .enforcedTextAttributes(deriveAttributes(prefix, dark))
                .create()
        }
    }

    private fun deriveAttributes(seed: String, darkEditor: Boolean): TextAttributes {
        val hue = ((seed.hashCode() and 0x7fffffff) % 360) / 360f
        val saturation: Float
        val brightness: Float
        if (darkEditor) {
            saturation = 0.55f
            brightness = 0.85f
        } else {
            saturation = 0.75f
            brightness = 0.55f
        }
        return TextAttributes(Color.getHSBColor(hue, saturation, brightness), null, null, null, Font.PLAIN)
    }

    private fun isDarkEditor(): Boolean {
        val bg = EditorColorsManager.getInstance().globalScheme.defaultBackground
        return (bg.red + bg.green + bg.blue) / 3 < 128
    }
}
