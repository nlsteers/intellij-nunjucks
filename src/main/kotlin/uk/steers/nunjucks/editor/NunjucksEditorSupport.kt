package uk.steers.nunjucks.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.lang.BracePair
import com.intellij.lang.Commenter
import com.intellij.lang.Language
import com.intellij.lang.PairedBraceMatcher
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.templateLanguages.MultipleLangCommentProvider
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.file.NunjucksFileViewProvider
import uk.steers.nunjucks.psi.NunjucksBlock
import uk.steers.nunjucks.psi.NunjucksTokenSets
import uk.steers.nunjucks.psi.NunjucksTypes

class NunjucksBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    private companion object {
        val PAIRS = arrayOf(
            BracePair(NunjucksTypes.LPAREN, NunjucksTypes.RPAREN, false),
            BracePair(NunjucksTypes.LBRACKET, NunjucksTypes.RBRACKET, false),
            BracePair(NunjucksTypes.LBRACE, NunjucksTypes.RBRACE, false),
            BracePair(NunjucksTypes.VAR_START, NunjucksTypes.VAR_END, true),
            BracePair(NunjucksTypes.BLOCK_START, NunjucksTypes.BLOCK_END, true),
        )
    }
}

class NunjucksCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null
    override fun getBlockCommentPrefix(): String = "{#"
    override fun getBlockCommentSuffix(): String = "#}"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}

/** Comment with `{# #}` everywhere in a Nunjucks file, including its HTML parts – HTML comments are still rendered. */
class NunjucksMultipleLangCommentProvider : MultipleLangCommentProvider {
    override fun canProcess(file: PsiFile, viewProvider: FileViewProvider): Boolean = viewProvider is NunjucksFileViewProvider

    override fun getLineCommenter(file: PsiFile, editor: Editor, lineStartLanguage: Language, lineEndLanguage: Language): Commenter =
        NunjucksCommenter()
}

class NunjucksQuoteHandler : SimpleTokenSetQuoteHandler(NunjucksTypes.STRING)

/** Highlights and navigates between the tags of a block: `{% if %}` ↔ `{% elif %}` ↔ `{% else %}` ↔ `{% endif %}`. */
class NunjucksCodeBlockSupportHandler : CodeBlockSupportHandler {
    override fun getCodeBlockMarkerRanges(elementAtCursor: PsiElement): List<TextRange> {
        if (elementAtCursor.elementType !in NunjucksTokenSets.TAG_KEYWORDS) return emptyList()
        val block = NunjucksBlock.containingTag(elementAtCursor) ?: return emptyList()
        return block.keywordTokens().map { it.textRange }
    }

    override fun getCodeBlockRange(elementAtCursor: PsiElement): TextRange {
        val block = NunjucksBlock.containingTag(elementAtCursor) ?: NunjucksBlock.enclosing(elementAtCursor).firstOrNull()
        return block?.statement?.textRange ?: TextRange.EMPTY_RANGE
    }
}
