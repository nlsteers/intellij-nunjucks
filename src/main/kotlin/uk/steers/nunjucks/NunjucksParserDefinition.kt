package uk.steers.nunjucks

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import uk.steers.nunjucks.file.NunjucksFile
import uk.steers.nunjucks.lexer.NunjucksLexer
import uk.steers.nunjucks.parser.NunjucksParser
import uk.steers.nunjucks.psi.NunjucksTokenSets
import uk.steers.nunjucks.psi.NunjucksTypes

class NunjucksParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = NunjucksLexer()

    override fun createParser(project: Project?) = NunjucksParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = NunjucksTokenSets.COMMENTS

    override fun getStringLiteralElements(): TokenSet = NunjucksTokenSets.STRINGS

    override fun createElement(node: ASTNode): PsiElement = NunjucksTypes.Factory.createElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NunjucksFile(viewProvider)

    companion object {
        @JvmField
        val FILE = IFileElementType(NunjucksLanguage)
    }
}
