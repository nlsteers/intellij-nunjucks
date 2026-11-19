package uk.steers.nunjucks.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import uk.steers.nunjucks.file.NunjucksFileViewProvider
import uk.steers.nunjucks.lexer.NunjucksLexer
import uk.steers.nunjucks.psi.NunjucksTokenSets
import uk.steers.nunjucks.psi.NunjucksTypes
import com.intellij.lang.html.HTMLLanguage
import uk.steers.nunjucks.highlighting.NunjucksHighlighterColours as C

class NunjucksSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = NunjucksLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = pack(ATTRIBUTES[tokenType])

    companion object {
        private val ATTRIBUTES: Map<IElementType, TextAttributesKey> = buildMap {
            NunjucksTokenSets.DELIMITERS.types.forEach { put(it, C.DELIMITER) }
            NunjucksTokenSets.KEYWORDS.types.forEach { put(it, C.KEYWORD) }
            NunjucksTokenSets.CONSTANTS.types.forEach { put(it, C.CONSTANT) }
            NunjucksTokenSets.OPERATORS.types.forEach { put(it, C.OPERATOR) }
            put(NunjucksTypes.TAG_NAME, C.CUSTOM_TAG)
            put(NunjucksTypes.COMMENT, C.COMMENT)
            put(NunjucksTypes.STRING, C.STRING)
            put(NunjucksTypes.NUMBER, C.NUMBER)
            put(NunjucksTypes.REGEX, C.REGEX)
            put(NunjucksTypes.IDENTIFIER, C.IDENTIFIER)
            put(NunjucksTypes.DOT, C.DOT)
            put(NunjucksTypes.COMMA, C.COMMA)
            put(NunjucksTypes.COLON, C.OPERATOR)
            put(NunjucksTypes.LPAREN, C.PARENTHESES)
            put(NunjucksTypes.RPAREN, C.PARENTHESES)
            put(NunjucksTypes.LBRACKET, C.BRACKETS)
            put(NunjucksTypes.RBRACKET, C.BRACKETS)
            put(NunjucksTypes.LBRACE, C.BRACES)
            put(NunjucksTypes.RBRACE, C.BRACES)
            put(TokenType.BAD_CHARACTER, C.BAD_CHARACTER)
        }
    }
}

class NunjucksSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = NunjucksSyntaxHighlighter()
}

/** Highlights Nunjucks tokens and delegates template text to the template data language (HTML by default). */
class NunjucksTemplateHighlighter(project: Project?, virtualFile: VirtualFile?, colours: EditorColorsScheme) :
    LayeredLexerEditorHighlighter(NunjucksSyntaxHighlighter(), colours) {
    init {
        val dataLanguage = if (project != null && virtualFile != null) {
            NunjucksFileViewProvider.templateDataLanguage(PsiManager.getInstance(project), virtualFile)
        } else {
            HTMLLanguage.INSTANCE
        }
        val dataHighlighter = dataLanguage.associatedFileType
            ?.let { SyntaxHighlighterFactory.getSyntaxHighlighter(it, project, virtualFile) }
            ?: PlainSyntaxHighlighter()
        registerLayer(NunjucksTypes.TEMPLATE_TEXT, LayerDescriptor(dataHighlighter, ""))
    }
}

class NunjucksEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(project: Project?, fileType: FileType, virtualFile: VirtualFile?, colours: EditorColorsScheme) =
        NunjucksTemplateHighlighter(project, virtualFile, colours)
}
