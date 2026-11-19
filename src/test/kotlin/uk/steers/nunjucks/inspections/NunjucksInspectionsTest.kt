package uk.steers.nunjucks.inspections

import com.intellij.openapi.fileTypes.FileTypeManager
import uk.steers.nunjucks.NunjucksTestCase
import uk.steers.nunjucks.file.NunjucksFileType

class NunjucksInspectionsTest : NunjucksTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(
            NunjucksUnknownFilterInspection(), NunjucksUnknownTestInspection(), NunjucksUnresolvedTemplateInspection(),
            NunjucksUnresolvedMacroInspection(), NunjucksUnusedImportInspection(), NunjucksUnusedVariableInspection(),
            NunjucksMismatchedEndTagInspection(), NunjucksUnknownKeywordArgumentInspection(), NunjucksMacroOptionsInspection(),
            NunjucksMismatchedBracketInspection(), NunjucksDuplicateBlockInspection(),
        )
        myFixture.addFileToProject("views/macros/forms.njk", "{% macro field(name, type=\"text\") %}{{ name }}{% endmacro %}{% macro label(text) %}{{ text }}{% endmacro %}")
        myFixture.addFileToProject("node_modules/govuk-frontend/dist/govuk/components/button/macro.njk", "{% macro govukButton(params) %}{% endmacro %}")
        myFixture.addFileToProject(
            "node_modules/govuk-frontend/dist/govuk/components/button/macro-options.json",
            """[{ "name": "text", "type": "string", "required": true, "description": "" },
               { "name": "element", "type": "string", "required": false, "description": "", "deprecated": "5.1.0" }]""",
        )
        myFixture.addFileToProject("app/env.js", "env.addFilter('formatDate', f); env.addTest('weekday', t);")
    }

    fun testFilters() = check(
        """
        {{ x | upper | formatDate | <warning descr="Unknown filter 'uppr'">uppr</warning> }}
        """,
    )

    fun testTests() = check(
        """
        {% if x is defined and x is weekday and x is <warning descr="Unknown test 'definned'">definned</warning> %}{% endif %}
        """,
    )

    fun testTemplates() = check(
        """
        {% extends "<error descr="Cannot resolve template 'layouts/nope.njk'">layouts/nope.njk</error>" %}
        {% include "missing.njk" ignore missing %}
        {% include dynamicName %}
        {% from "macros/forms.njk" import field, <error descr="'nope' is not a macro in forms.njk">nope</error> %}
        {{ field("a") }}{{ nope() }}
        """,
    )

    fun testNamespaceMembers() = check(
        """
        {% import "macros/forms.njk" as forms %}
        {{ forms.field("a") }}{{ forms.<error descr="'missing' is not a macro in forms.njk">missing</error>() }}
        """,
    )

    fun testUnusedImports() = check(
        """
        {% from "macros/forms.njk" import field, <warning descr="Unused import 'label'">label</warning> %}
        <warning descr="Unused import 'f'">{% import "macros/forms.njk" as f %}</warning>
        {{ field("x") }}
        """,
    )

    fun testUnusedVariablesOnlyInsideScopes() = check(
        """
        {% set pageTitle = "kept for the layout" %}
        {% macro m() %}{% set <warning descr="Variable 'unused' is never used">unused</warning> = 1 %}{% set used = 2 %}{{ used }}{% endmacro %}
        """,
    )

    fun testMismatchedEndTags() = check(
        """
        {% asyncEach x in xs %}{{ x }}{% <error descr="{% asyncEach %} should be closed with {% endeach %}">endfor</error> %}
        {% block content %}{% endblock <error descr="{% endblock %} name 'other' doesn't match block 'content'">other</error> %}
        """,
    )

    fun testKeywordArguments() = check(
        """
        {% from "macros/forms.njk" import field %}
        {{ field("a", type="email", <warning descr="Macro 'field' has no parameter 'tpye'">tpye</warning>="x") }}
        """,
    )

    fun testMacroOptions() = check(
        """
        {% from "govuk/components/button/macro.njk" import govukButton %}
        {{ govukButton({ text: "Go", <weak_warning descr="Unknown option 'txet'">txet</weak_warning>: "x", <weak_warning descr="Option 'element' is deprecated since 5.1.0">element</weak_warning>: "a" }) }}
        """,
    )

    fun testMismatchedBrackets() = check(
        """
        {% set items = [{ a: 1 <warning descr="Expected '}' but found ']'">]</warning><warning descr="Expected ']' but found '}'">}</warning> %}
        """,
    )

    fun testDuplicateBlocks() = check(
        """
        {% block a %}{% endblock %}{% block <error descr="Block 'a' is already defined in this template">a</error> %}{% endblock %}
        """,
    )

    fun testOrphanEndTag() = check(
        """
        <error descr="Unexpected {% endif %}: no open block to close">{% endif %}</error>
        """,
    )

    fun testHtmlFileWithNunjucksTagsIsNunjucks() {
        val template = myFixture.addFileToProject("views/page.html", "{% extends \"base.html\" %}\n<p>hi</p>")
        val plain = myFixture.addFileToProject("views/plain.html", "<p>{{ angular }}</p>")
        assertEquals(NunjucksFileType, FileTypeManager.getInstance().getFileTypeByFile(template.virtualFile))
        assertNotSame(NunjucksFileType, FileTypeManager.getInstance().getFileTypeByFile(plain.virtualFile))
    }

    private fun check(text: String) {
        val file = myFixture.addFileToProject("views/page.njk", text.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.checkHighlighting(true, false, true)
    }
}
