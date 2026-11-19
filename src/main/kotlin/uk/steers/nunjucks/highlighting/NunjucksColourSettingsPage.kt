package uk.steers.nunjucks.highlighting

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import uk.steers.nunjucks.NunjucksIcons
import uk.steers.nunjucks.highlighting.NunjucksHighlighterColours as C

class NunjucksColourSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Nunjucks"

    override fun getIcon() = NunjucksIcons.FILE

    override fun getHighlighter() = NunjucksSyntaxHighlighter()

    override fun getAttributeDescriptors() = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap() = TAGS

    override fun getDemoText() = """
        {# Page layout #}
        {% extends "layouts/base.njk" %}
        {% from "govuk/components/button/macro.njk" import govukButton %}

        {% set title = "Check your details" | upper %}

        {% macro <macro>field</macro>(<param>name</param>, <param>label</param>="Name") %}
          <label for="{{ name }}">{{ label | <filter>escape</filter> }}</label>
        {% endmacro %}

        {% block <block>content</block> %}
          {% for item in items %}
            {{ loop.index ~ ": " ~ item.name | <filter>default</filter>("unknown") }}
          {% else %}
            <p>Nothing here</p>
          {% endfor %}

          {% if count is <test>divisibleby</test>(3) and not hidden %}
            {{ <call>govukButton</call>({ <key>text</key>: "Continue", <key>classes</key>: "app-button" }) }}
          {% endif %}

          {{ value | <filter>replace</filter>(r/\d+/g, "#") }}
          {% customTag foo %}
        {% endblock %}
    """.trimIndent()

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Delimiters//Tag and output delimiters", C.DELIMITER),
            AttributesDescriptor("Keyword", C.KEYWORD),
            AttributesDescriptor("Extension tag", C.CUSTOM_TAG),
            AttributesDescriptor("Constant", C.CONSTANT),
            AttributesDescriptor("Comment", C.COMMENT),
            AttributesDescriptor("String", C.STRING),
            AttributesDescriptor("Number", C.NUMBER),
            AttributesDescriptor("Regular expression", C.REGEX),
            AttributesDescriptor("Operator", C.OPERATOR),
            AttributesDescriptor("Identifier", C.IDENTIFIER),
            AttributesDescriptor("Braces and Operators//Dot", C.DOT),
            AttributesDescriptor("Braces and Operators//Comma", C.COMMA),
            AttributesDescriptor("Braces and Operators//Parentheses", C.PARENTHESES),
            AttributesDescriptor("Braces and Operators//Brackets", C.BRACKETS),
            AttributesDescriptor("Braces and Operators//Braces", C.BRACES),
            AttributesDescriptor("Filter", C.FILTER),
            AttributesDescriptor("Test", C.TEST),
            AttributesDescriptor("Macro declaration", C.MACRO_DECLARATION),
            AttributesDescriptor("Macro call", C.MACRO_CALL),
            AttributesDescriptor("Parameter", C.PARAMETER),
            AttributesDescriptor("Block name", C.BLOCK_NAME),
            AttributesDescriptor("Object key", C.DICT_KEY),
            AttributesDescriptor("Keyword argument", C.KEYWORD_ARGUMENT),
            AttributesDescriptor("Bad character", C.BAD_CHARACTER),
        )

        val TAGS = mapOf(
            "macro" to C.MACRO_DECLARATION,
            "param" to C.PARAMETER,
            "filter" to C.FILTER,
            "test" to C.TEST,
            "block" to C.BLOCK_NAME,
            "call" to C.MACRO_CALL,
            "key" to C.DICT_KEY,
        )
    }
}
