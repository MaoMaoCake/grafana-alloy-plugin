package com.maomaocake.grafanaalloyplugin.references

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.maomaocake.grafanaalloyplugin.lexer.AlloyLexerAdapter
import com.maomaocake.grafanaalloyplugin.psi.AlloyBlockLabel
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyPsiUtil
import com.maomaocake.grafanaalloyplugin.psi.AlloyTokens

/**
 * Tells the platform how to find and display usages of Alloy named elements.
 *
 * The load-bearing piece is [getWordsScanner]: when the user invokes Rename / Find Usages on
 * an [AlloyBlockLabel], the platform does a text-based search for occurrences of the label's
 * name. Our scanner identifies IDENT tokens and STRINGs as "word" containers — so the `.d` in
 * `prometheus.remote_write.d.receiver` *and* the `"d"` in the declaration both surface as
 * candidates, and the platform then walks up the PSI to match them against registered
 * references.
 */
class AlloyFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        AlloyLexerAdapter(),
        /* identifierTokenSet = */ TokenSet.create(AlloyElementTypes.IDENT),
        /* commentTokenSet   = */ AlloyTokens.COMMENTS,
        /* literalTokenSet   = */ AlloyTokens.STRINGS,
    )

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is AlloyBlockLabel

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String =
        if (element is AlloyBlockLabel) "Alloy component label" else ""

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? AlloyBlockLabel)?.let { AlloyPsiUtil.unquoteLabel(it) } ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        getDescriptiveName(element)
}
