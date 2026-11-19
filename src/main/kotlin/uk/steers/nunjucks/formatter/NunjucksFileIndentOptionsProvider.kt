// Derived from HbFileIndentOptionsProvider in the JetBrains Handlebars plugin (https://github.com/JetBrains/intellij-plugins),
// Copyright 2000-2025 JetBrains s.r.o. and contributors, Apache License 2.0. Modified for Nunjucks; see NOTICE.

package uk.steers.nunjucks.formatter

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.FileIndentOptionsProvider
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import uk.steers.nunjucks.file.NunjucksFileType

/** Nunjucks files use the indent settings of their template data language (HTML by default). */
class NunjucksFileIndentOptionsProvider : FileIndentOptionsProvider() {
    override fun getIndentOptions(project: Project, settings: CodeStyleSettings, file: VirtualFile): CommonCodeStyleSettings.IndentOptions? {
        if (file.fileType != NunjucksFileType) return null
        val provider = PsiManager.getInstance(project).findViewProvider(file) as? TemplateLanguageFileViewProvider ?: return null
        return settings.getCommonSettings(provider.templateDataLanguage).indentOptions
    }
}
