package com.maomaocake.grafanaalloyplugin.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.maomaocake.grafanaalloyplugin.AlloyLanguage
import com.maomaocake.grafanaalloyplugin.lexer.AlloyLexerAdapter
import com.maomaocake.grafanaalloyplugin.psi.AlloyElementTypes
import com.maomaocake.grafanaalloyplugin.psi.AlloyFile
import com.maomaocake.grafanaalloyplugin.psi.AlloyTokens

class AlloyParserDefinition : ParserDefinition {

    companion object {
        val FILE: IFileElementType = IFileElementType(AlloyLanguage)
    }

    override fun createLexer(project: Project?): Lexer = AlloyLexerAdapter()
    override fun createParser(project: Project?) = AlloyParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = AlloyTokens.COMMENTS
    override fun getStringLiteralElements(): TokenSet = AlloyTokens.STRINGS
    override fun createElement(node: ASTNode?): PsiElement = AlloyElementTypes.Factory.createElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = AlloyFile(viewProvider)
}
