package uk.steers.nunjucks.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import uk.steers.nunjucks.NunjucksTestCase
import java.io.File

/**
 * Diagnostic: copies the templates (and nunjucks setup) of the project at `nunjucks.realProject` into the test
 * project, runs every Nunjucks inspection over its own templates and prints what is reported.
 */
class NunjucksRealProjectTest : NunjucksTestCase() {
    fun testReportProblems() {
        val root = System.getProperty("nunjucks.realProject")?.let(::File)?.takeIf { it.isDirectory } ?: return
        myFixture.enableInspections(
            NunjucksUnknownFilterInspection(), NunjucksUnknownTestInspection(), NunjucksUnresolvedTemplateInspection(),
            NunjucksUnresolvedMacroInspection(), NunjucksUnusedImportInspection(), NunjucksUnusedVariableInspection(),
            NunjucksMismatchedEndTagInspection(), NunjucksUnknownKeywordArgumentInspection(), NunjucksMacroOptionsInspection(),
            NunjucksMismatchedBracketInspection(), NunjucksDuplicateBlockInspection(),
        )
        val copied = listOf(
            "src",
            "node_modules/govuk-frontend/dist/govuk/components",
            "node_modules/govuk-frontend/dist/govuk/template.njk",
            "node_modules/@govuk-one-login/frontend-ui/build",
            "node_modules/@govuk-one-login/di-ipv-cri-common-express/src",
        )
        copied.map { File(root, it) }.filter { it.exists() }.forEach { source ->
            val target = source.relativeTo(root).path
            if (source.isDirectory) {
                source.walkTopDown().filter { it.isFile && it.extension in setOf("njk", "json", "js", "ts", "mjs") }.forEach {
                    myFixture.addFileToProject(it.relativeTo(root).path, it.readText())
                }
            } else {
                myFixture.addFileToProject(target, source.readText())
            }
        }

        val report = mutableListOf<String>()
        File(root, "src").walkTopDown().filter { it.isFile && it.extension == "njk" }.forEach { template ->
            val path = template.relativeTo(root).path
            myFixture.configureFromTempProjectFile(path)
            myFixture.doHighlighting()
                .filter { it.severity >= HighlightSeverity.WEAK_WARNING && it.description != null }
                .forEach { report.add("$path:${line(it)}: [${it.severity}] ${it.description}") }
        }
        println("REAL PROJECT PROBLEMS (${report.size}):\n" + report.joinToString("\n"))
    }

    private fun line(info: HighlightInfo) = myFixture.editor.document.getLineNumber(info.startOffset) + 1
}
