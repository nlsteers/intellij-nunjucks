package uk.steers.nunjucks.editor

import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.actionSystem.IdeActions
import uk.steers.nunjucks.NunjucksTestCase

class NunjucksEditorTest : NunjucksTestCase() {
    fun testHtmlAndNunjucksHighlightWithoutErrors() {
        myFixture.configureByText(
            "page.njk",
            """
            {% extends "base.njk" %}
            {% block content %}
              <div class="govuk-{{ modifier }}" {% if hidden %}hidden{% endif %}>
                {% for item in items %}
                  <a href="{{ item.href }}">{{ item.text | escape }}</a>
                {% endfor %}
              </div>
            {% endblock %}
            """.trimIndent(),
        )
        myFixture.checkHighlighting(false, false, false)
    }

    fun testUnclosedBlockIsReportedOnOpeningTag() {
        myFixture.configureByText(
            "page.njk",
            """
            <error descr="{% for %} is not closed: expected {% endfor %}">{% for x in xs %}</error>
              {{ x }}
            """.trimIndent(),
        )
        myFixture.checkHighlighting(false, false, false)
    }

    fun testAsyncEachExpectsEndeach() {
        myFixture.configureByText("page.njk", "<error descr=\"{% asyncEach %} is not closed: expected {% endeach %}\">{% asyncEach x in xs %}</error>{{ x }}")
        myFixture.checkHighlighting(false, false, false)
    }

    fun testUnterminatedString() {
        myFixture.configureByText("page.njk", "{{ <error descr=\"Unterminated string\">\"abc </error>}}")
        myFixture.checkHighlighting(false, false, false)
    }

    fun testFolding() {
        val marked = """
            {% if x %}<fold text='...'>
              <p>yes</p>
            </fold>{% endif %}
            <fold text='{# ... #}'>{# a
               comment #}</fold>
            <fold text='{{ govukButton(...) }}'>{{ govukButton({
              text: "Go"
            }) }}</fold>
            """.trimIndent()
        val file = kotlin.io.path.createTempFile("folding", ".njk").toFile().apply { writeText(marked) }
        myFixture.testFolding(file.path)
    }

    fun testDoubleBraceInsertsClosingDelimiter() {
        myFixture.configureByText("page.njk", "<p>{<caret></p>")
        myFixture.type("{")
        myFixture.checkResult("<p>{{ <caret> }}</p>")
    }

    fun testPercentInsertsClosingDelimiter() {
        myFixture.configureByText("page.njk", "{<caret>")
        myFixture.type("%")
        myFixture.checkResult("{% <caret> %}")
    }

    fun testTypingTagEndInsertsEndTag() {
        myFixture.configureByText("page.njk", "{% if x %<caret>")
        myFixture.type("}")
        myFixture.checkResult("{% if x %}<caret>{% endif %}")
    }

    fun testTypingTagEndDoesNotDuplicateEndTag() {
        myFixture.configureByText("page.njk", "{% if x %<caret>{% endif %}")
        myFixture.type("}")
        myFixture.checkResult("{% if x %}<caret>{% endif %}")
    }

    fun testCommentLineInHtmlUsesNunjucksComment() {
        myFixture.configureByText("page.njk", "<p><caret>hello</p>")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("{#<p>hello</p>#}")
    }

    fun testCodeBlockMarkersCoverAllTags() {
        myFixture.configureByText("page.njk", "{% i<caret>f a %}1{% elif b %}2{% else %}3{% endif %}")
        val element = nunjucksPsi(myFixture.file).findElementAt(myFixture.caretOffset)!!
        val ranges = CodeBlockSupportHandler.findMarkersRanges(element).map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
        assertEquals(listOf("if", "elif", "else", "endif"), ranges)
    }
}
