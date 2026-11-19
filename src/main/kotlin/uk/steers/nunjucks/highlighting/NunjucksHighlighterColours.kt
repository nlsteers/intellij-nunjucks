package uk.steers.nunjucks.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

object NunjucksHighlighterColours {
    @JvmField val DELIMITER = createTextAttributesKey("NUNJUCKS_DELIMITER", Default.KEYWORD)
    @JvmField val KEYWORD = createTextAttributesKey("NUNJUCKS_KEYWORD", Default.KEYWORD)
    @JvmField val CUSTOM_TAG = createTextAttributesKey("NUNJUCKS_CUSTOM_TAG", Default.METADATA)
    @JvmField val CONSTANT = createTextAttributesKey("NUNJUCKS_CONSTANT", Default.CONSTANT)
    @JvmField val COMMENT = createTextAttributesKey("NUNJUCKS_COMMENT", Default.BLOCK_COMMENT)
    @JvmField val STRING = createTextAttributesKey("NUNJUCKS_STRING", Default.STRING)
    @JvmField val NUMBER = createTextAttributesKey("NUNJUCKS_NUMBER", Default.NUMBER)
    @JvmField val REGEX = createTextAttributesKey("NUNJUCKS_REGEX", Default.VALID_STRING_ESCAPE)
    @JvmField val OPERATOR = createTextAttributesKey("NUNJUCKS_OPERATOR", Default.OPERATION_SIGN)
    @JvmField val IDENTIFIER = createTextAttributesKey("NUNJUCKS_IDENTIFIER", Default.IDENTIFIER)
    @JvmField val DOT = createTextAttributesKey("NUNJUCKS_DOT", Default.DOT)
    @JvmField val COMMA = createTextAttributesKey("NUNJUCKS_COMMA", Default.COMMA)
    @JvmField val PARENTHESES = createTextAttributesKey("NUNJUCKS_PARENTHESES", Default.PARENTHESES)
    @JvmField val BRACKETS = createTextAttributesKey("NUNJUCKS_BRACKETS", Default.BRACKETS)
    @JvmField val BRACES = createTextAttributesKey("NUNJUCKS_BRACES", Default.BRACES)
    @JvmField val BAD_CHARACTER = createTextAttributesKey("NUNJUCKS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

    // semantic, applied by the annotator
    @JvmField val FILTER = createTextAttributesKey("NUNJUCKS_FILTER", Default.STATIC_METHOD)
    @JvmField val TEST = createTextAttributesKey("NUNJUCKS_TEST", Default.STATIC_METHOD)
    @JvmField val MACRO_DECLARATION = createTextAttributesKey("NUNJUCKS_MACRO_DECLARATION", Default.FUNCTION_DECLARATION)
    @JvmField val MACRO_CALL = createTextAttributesKey("NUNJUCKS_MACRO_CALL", Default.FUNCTION_CALL)
    @JvmField val PARAMETER = createTextAttributesKey("NUNJUCKS_PARAMETER", Default.PARAMETER)
    @JvmField val BLOCK_NAME = createTextAttributesKey("NUNJUCKS_BLOCK_NAME", Default.CLASS_NAME)
    @JvmField val DICT_KEY = createTextAttributesKey("NUNJUCKS_DICT_KEY", Default.INSTANCE_FIELD)
    @JvmField val KEYWORD_ARGUMENT = createTextAttributesKey("NUNJUCKS_KEYWORD_ARGUMENT", Default.PARAMETER)
}
