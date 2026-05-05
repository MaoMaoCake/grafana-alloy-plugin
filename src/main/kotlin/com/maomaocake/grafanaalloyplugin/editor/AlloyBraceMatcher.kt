package com.maomaocake.grafanaalloyplugin.editor

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes

class AlloyBraceMatcher : PairedBraceMatcher {

    private val pairs = arrayOf(
        BracePair(AlloyElementTypes.LCURLY, AlloyElementTypes.RCURLY, /* structural = */ true),
        BracePair(AlloyElementTypes.LBRACK, AlloyElementTypes.RBRACK, /* structural = */ false),
        BracePair(AlloyElementTypes.LPAREN, AlloyElementTypes.RPAREN, /* structural = */ false),
    )

    override fun getPairs(): Array<BracePair> = pairs
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
