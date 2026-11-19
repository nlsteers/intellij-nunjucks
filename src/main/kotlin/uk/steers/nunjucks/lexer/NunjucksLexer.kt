package uk.steers.nunjucks.lexer

import com.intellij.lexer.DelegateLexer
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.MergingLexerAdapter
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import uk.steers.nunjucks.psi.NunjucksTypes

/**
 * Template text that is only whitespace (e.g. between two tags) is reported as whitespace
 * so the parser and formatter can treat it as such, mirroring the bundled Handlebars plugin.
 */
class NunjucksLexer : DelegateLexer(
    MergingLexerAdapter(FlexAdapter(_NunjucksLexer(null)), TokenSet.create(NunjucksTypes.TEMPLATE_TEXT)),
) {
    override fun getTokenType(): IElementType? {
        val type = super.getTokenType()
        if (type == NunjucksTypes.TEMPLATE_TEXT && isBlank()) return TokenType.WHITE_SPACE
        return type
    }

    private fun isBlank(): Boolean {
        val buffer = bufferSequence
        for (i in tokenStart until tokenEnd) {
            if (!buffer[i].isWhitespace()) return false
        }
        return true
    }
}
