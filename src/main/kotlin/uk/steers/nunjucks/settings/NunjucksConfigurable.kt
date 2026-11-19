package uk.steers.nunjucks.settings

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

class NunjucksConfigurable(private val project: Project) : BoundConfigurable("Nunjucks") {
    private val settings = NunjucksSettings.getInstance(project)
    private val appSettings = NunjucksAppSettings.getInstance()

    private var templateRoots = ""
    private var extraFilters = ""
    private var extraTests = ""
    private var extraGlobals = ""
    private var detectInHtml = true

    override fun createPanel(): DialogPanel {
        load()
        return panel {
            group("Template Search Paths") {
                row {
                    textArea().bindText(::templateRoots).rows(4).align(AlignX.FILL)
                        .comment(
                            "One per line, relative to the project root or absolute – as passed to <code>nunjucks.configure()</code>. " +
                                "Leave empty to use every <code>views</code> directory plus the govuk-frontend and @govuk-one-login packages.",
                        )
                }
            }
            group("Environment") {
                row("Extra filters:") { textField().bindText(::extraFilters).align(AlignX.FILL).comment("Comma separated. Filters registered with env.addFilter(\"name\", …) are found automatically.") }
                row("Extra tests:") { textField().bindText(::extraTests).align(AlignX.FILL) }
                row("Extra globals:") { textField().bindText(::extraGlobals).align(AlignX.FILL) }
            }
            group("File Types") {
                row {
                    checkBox("Treat .html files containing Nunjucks tags as Nunjucks templates").bindSelected(::detectInHtml)
                        .comment("Applies to all projects. Files are re-detected when this changes.")
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        settings.update {
            this.templateRoots = lines(this@NunjucksConfigurable.templateRoots)
            this.extraFilters = names(this@NunjucksConfigurable.extraFilters)
            this.extraTests = names(this@NunjucksConfigurable.extraTests)
            this.extraGlobals = names(this@NunjucksConfigurable.extraGlobals)
        }
        if (appSettings.state.detectInHtmlFiles != detectInHtml) {
            appSettings.state.detectInHtmlFiles = detectInHtml
            (FileTypeManager.getInstance() as FileTypeManagerEx).makeFileTypesChange("Nunjucks HTML detection changed") {}
        }
        DaemonCodeAnalyzer.getInstance(project).restart("Nunjucks settings changed")
    }

    override fun reset() {
        load()
        super.reset()
    }

    private fun load() {
        val state = settings.state
        templateRoots = state.templateRoots.joinToString("\n")
        extraFilters = state.extraFilters.joinToString(", ")
        extraTests = state.extraTests.joinToString(", ")
        extraGlobals = state.extraGlobals.joinToString(", ")
        detectInHtml = appSettings.state.detectInHtmlFiles
    }

    private fun lines(text: String) = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

    private fun names(text: String) = text.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().toMutableList()
}
