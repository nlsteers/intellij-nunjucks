package uk.steers.nunjucks.reference

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import uk.steers.nunjucks.NunjucksTestCase
import uk.steers.nunjucks.navigation.NunjucksBlockInheritance
import uk.steers.nunjucks.psi.NjkBlockStatement
import uk.steers.nunjucks.psi.NjkMacroStatement
import com.intellij.psi.util.PsiTreeUtil

class NunjucksSemanticsTest : NunjucksTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "node_modules/govuk-frontend/dist/govuk/components/button/macro.njk",
            "{% macro govukButton(params) %}\n  {%- include \"./template.njk\" -%}\n{% endmacro %}",
        )
        myFixture.addFileToProject("node_modules/govuk-frontend/dist/govuk/components/button/template.njk", "<button>{{ params.text }}</button>")
        myFixture.addFileToProject(
            "node_modules/govuk-frontend/dist/govuk/components/button/macro-options.json",
            """
            [
              { "name": "text", "type": "string", "required": true, "description": "Button text." },
              { "name": "href", "type": "string", "required": false, "description": "Link target." },
              { "name": "attributes", "type": "object", "required": false, "description": "HTML attributes." },
              { "name": "label", "type": "object", "required": false, "isComponent": true, "description": "Label." }
            ]
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "node_modules/govuk-frontend/dist/govuk/components/label/macro-options.json",
            """[{ "name": "text", "type": "string", "required": false, "description": "Label text." }, { "name": "for", "type": "string", "required": false, "description": "For." }]""",
        )
        myFixture.addFileToProject("views/layouts/base.njk", "<main>{% block content %}{% endblock %}</main>")
        myFixture.addFileToProject(
            "views/macros/forms.njk",
            "{# Renders a text field. #}\n{% macro field(name, value=\"\", type=\"text\") %}<input name=\"{{ name }}\">{% endmacro %}",
        )
        myFixture.addFileToProject("app/nunjucks.js", "env.addFilter('formatDate', (d) => d);\nenv.addGlobal(\"serviceName\", 'x');")
    }

    fun testResolvesTemplatePathsFromViewsAndGovukRoots() {
        val file = page(
            """
            {% extends "layouts/ba<caret>se.njk" %}
            {% from "govuk/components/button/macro.njk" import govukButton %}
            """,
        )
        assertEquals("base.njk", (myFixture.elementAtCaret as PsiFile).name)
        assertTrue(NunjucksTemplateResolver.resolve(file, "govuk/components/button/macro.njk") != null)
    }

    fun testRelativeIncludeResolves() {
        val macro = myFixture.findFileInTempDir("node_modules/govuk-frontend/dist/govuk/components/button/macro.njk")
        val psi = psiManager.findFile(macro)!!
        assertEquals("template.njk", NunjucksTemplateResolver.resolve(psi, "./template.njk")?.name)
    }

    fun testImportedMacroCallResolvesToDefinition() {
        page(
            """
            {% from "govuk/components/button/macro.njk" import govukButton %}
            {{ govuk<caret>Button({ text: "Go" }) }}
            """,
        )
        val target = myFixture.elementAtCaret as NjkMacroStatement
        assertEquals("govukButton", target.name)
        assertEquals("macro.njk", target.containingFile.name)
    }

    fun testNamespacedMacroResolves() {
        page(
            """
            {% import "macros/forms.njk" as forms %}
            {{ forms.fi<caret>eld("email") }}
            """,
        )
        assertEquals("field", (myFixture.elementAtCaret as NjkMacroStatement).name)
    }

    fun testLoopVariableAndSetScopes() {
        page(
            """
            {% set title = "x" %}
            {% for item in items %}{% set inner = item %}{{ inn<caret>er }}{% endfor %}
            """,
        )
        assertEquals("inner", (myFixture.elementAtCaret as PsiNamedElement).name)
    }

    fun testSetInsideLoopIsNotVisibleAfterIt() {
        page("{% for item in items %}{% set inner = item %}{% endfor %}{{ inn<caret>er }}")
        assertNull(myFixture.file.findReferenceAt(myFixture.caretOffset)?.resolve())
    }

    fun testKeywordArgumentResolvesToParameter() {
        page("{% from \"macros/forms.njk\" import field %}{{ field(\"a\", ty<caret>pe=\"email\") }}")
        assertEquals("type", (myFixture.elementAtCaret as PsiNamedElement).name)
    }

    fun testFilterResolvesToJavaScriptRegistration() {
        page("{{ date | format<caret>Date }}")
        assertEquals("nunjucks.js", myFixture.elementAtCaret.containingFile.name)
    }

    fun testBlockOverridesParentBlock() {
        page("{% extends \"layouts/base.njk\" %}{% block con<caret>tent %}hi{% endblock %}")
        val block = PsiTreeUtil.findChildOfType(nunjucksPsi(myFixture.file), NjkBlockStatement::class.java)!!
        assertEquals("base.njk", NunjucksBlockInheritance.overriddenBlock(block)?.containingFile?.name)
    }

    fun testFindUsagesOfMacroAcrossFiles() {
        myFixture.addFileToProject("views/a.njk", "{% from \"macros/forms.njk\" import field %}{{ field(\"a\") }}")
        myFixture.addFileToProject("views/b.njk", "{% import \"macros/forms.njk\" as f %}{{ f.field(\"b\") }}")
        val forms = psiManager.findFile(myFixture.findFileInTempDir("views/macros/forms.njk"))!!
        val macro = PsiTreeUtil.findChildOfType(nunjucksPsi(forms), NjkMacroStatement::class.java)!!
        val usages = ReferencesSearch.search(macro).findAll().map { it.element.containingFile.name }.sorted()
        assertEquals(listOf("a.njk", "a.njk", "b.njk"), usages)
    }

    fun testRenameLocalVariable() {
        page("{% set na<caret>me = 1 %}{{ name }}{% if name %}{{ name + 1 }}{% endif %}")
        myFixture.renameElementAtCaret("label")
        myFixture.checkResult("{% set label = 1 %}{{ label }}{% if label %}{{ label + 1 }}{% endif %}")
    }

    fun testCompletesTagNamesWithEndTagForInnermostOpenBlock() {
        page("{% for x in xs %}{% <caret>")
        val lookups = myFixture.completeBasic().map { it.lookupString }
        assertEquals("endfor", lookups.first())
        assertContainsElements(lookups, "if", "set", "macro", "else")
    }

    fun testCompletingIfAddsEndTag() {
        page("{% i<caret> %}")
        myFixture.completeBasic()
        myFixture.type('\n')
        myFixture.checkResult("{% if <caret>%}{% endif %}")
    }

    fun testCompletesFiltersIncludingCustom() {
        page("{{ x | <caret> }}")
        val lookups = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(lookups, "upper", "default", "safe", "formatDate")
    }

    fun testCompletesTests() {
        page("{% if x is <caret> %}{% endif %}")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "defined", "divisibleby", "odd")
    }

    fun testCompletesVisibleNamesAndGlobals() {
        page("{% from \"macros/forms.njk\" import field %}{% set title = 1 %}{% for item in items %}{{ <caret> }}{% endfor %}")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "field", "title", "item", "loop", "range", "serviceName")
    }

    fun testCompletesLoopProperties() {
        page("{% for item in items %}{{ loop.<caret> }}{% endfor %}")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "index", "first", "last", "length")
    }

    fun testCompletesImportedNames() {
        page("{% from \"macros/forms.njk\" import <caret> %}")
        assertEquals(listOf("field"), myFixture.completeBasic().map { it.lookupString })
    }

    fun testCompletesGovukOptionKeys() {
        page("{% from \"govuk/components/button/macro.njk\" import govukButton %}{{ govukButton({ text: \"Go\", <caret> }) }}")
        val lookups = myFixture.completeBasic().map { it.lookupString }
        assertContainsElements(lookups, "href", "attributes", "label")
        assertDoesntContain(lookups, "text")
    }

    fun testCompletesNestedComponentOptionKeys() {
        page("{% from \"govuk/components/button/macro.njk\" import govukButton %}{{ govukButton({ label: { <caret> } }) }}")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "text", "for")
    }

    fun testCompletesMacroKeywordArguments() {
        page("{% from \"macros/forms.njk\" import field %}{{ field(\"a\", <caret>) }}")
        assertContainsElements(myFixture.completeBasic().map { it.lookupString }, "value=", "type=")
    }

    /** Creates views/page.njk (so it sits under an auto-detected `views` root) and opens it at `<caret>`. */
    private fun page(text: String): PsiFile {
        val source = text.trimIndent()
        val caret = source.indexOf("<caret>")
        val file = myFixture.addFileToProject("views/page.njk", source.replace("<caret>", ""))
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        if (caret >= 0) myFixture.editor.caretModel.moveToOffset(caret)
        return myFixture.file
    }
}
