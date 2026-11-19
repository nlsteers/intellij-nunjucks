package uk.steers.nunjucks.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleManager
import uk.steers.nunjucks.NunjucksTestCase
import java.io.File

class NunjucksFormatterTest : NunjucksTestCase() {
    override fun setUp() {
        super.setUp()
        val settings = CodeStyle.getSettings(project)
        settings.getCommonSettings(HTMLLanguage.INSTANCE).indentOptions!!.apply {
            INDENT_SIZE = 2
            CONTINUATION_INDENT_SIZE = 2
        }
        settings.getCustomSettings(HtmlCodeStyleSettings::class.java).HTML_SPACE_INSIDE_EMPTY_TAG = true
    }

    fun testIndentsBlockBodies() = doTest(
        """
        {% if x %}
        <p>a</p>
        {% for i in items %}
        <li>{{ i }}</li>
        {% endfor %}
        {% else %}
        <p>b</p>
        {% endif %}
        """,
        """
        {% if x %}
          <p>a</p>
          {% for i in items %}
            <li>{{ i }}</li>
          {% endfor %}
        {% else %}
          <p>b</p>
        {% endif %}
        """,
    )

    fun testBlocksInsideHtml() = doTest(
        """
        <div>
        {% block content %}
        <p>a</p>
        {% endblock %}
        </div>
        """,
        """
        <div>
          {% block content %}
            <p>a</p>
          {% endblock %}
        </div>
        """,
    )

    fun testMacroCallArguments() = doTest(
        """
        {{ govukButton({
        text: "Continue",
        attributes: {
        id: "go"
        }
        }) }}
        """,
        """
        {{ govukButton({
          text: "Continue",
          attributes: {
            id: "go"
          }
        }) }}
        """,
    )

    fun testInlineBlockStaysOnOneLine() = doTest(
        """
        <div>
          {% if a %}<strong>{{ a }}</strong>{% endif %}
        </div>
        """,
        """
        <div>
          {% if a %}<strong>{{ a }}</strong>{% endif %}
        </div>
        """,
    )

    fun testWhitespaceInsidePreIsKept() = doTest(
        """
        <div>
          <pre>
        {{ value | dump(2) }}</pre>
        </div>
        """,
        """
        <div>
          <pre>
        {{ value | dump(2) }}</pre>
        </div>
        """,
    )

    fun testDumpBlocks() {
        if (System.getProperty("nunjucks.dumpBlocks") == null) return
        for (text in listOf("<div>\n  <pre>\n{{ value }}</pre>\n</div>", "<div>\n  {% if a %}<strong>{{ a }}</strong>{% endif %}\n</div>")) {
            myFixture.configureByText("dump.njk", text)
            val builder = com.intellij.lang.LanguageFormatting.INSTANCE.forContext(myFixture.file)!!
            val model = builder.createModel(com.intellij.formatting.FormattingContext.create(myFixture.file, CodeStyle.getSettings(project)))
            val out = StringBuilder()
            com.intellij.formatting.FormattingModelDumper.dumpFormattingModel(model.rootBlock, 2, out)
            println("BLOCKS:\n$out")
        }
    }

    fun testFormattedCorpusIsStable() {
        val roots = System.getProperty("nunjucks.formatCorpus").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        if (roots.isEmpty()) return
        val changed = mutableListOf<String>()
        roots.map(::File).flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "njk" }.toList() }
            .forEachIndexed { index, template ->
                val original = template.readText()
                val formatted = format(original, "corpus$index.njk")
                if (formatted != original) changed.add("=== ${template.path}\n" + diff(original, formatted))
            }
        println(changed.joinToString("\n"))
        println("${changed.size} files changed by formatting")
    }

    private fun doTest(before: String, after: String) {
        assertEquals(after.trimIndent(), format(before.trimIndent(), "test.njk"))
    }

    private fun format(text: String, name: String): String {
        myFixture.configureByText(name, text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        return myFixture.editor.document.text
    }

    private fun diff(a: String, b: String): String {
        val left = a.lines()
        val right = b.lines()
        return (0 until maxOf(left.size, right.size))
            .filter { left.getOrNull(it) != right.getOrNull(it) }
            .take(12)
            .joinToString("\n") { "${it + 1}: -${left.getOrNull(it)}\n${it + 1}: +${right.getOrNull(it)}" }
    }
}
