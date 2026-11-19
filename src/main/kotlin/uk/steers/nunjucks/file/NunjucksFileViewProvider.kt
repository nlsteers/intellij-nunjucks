// Derived from HbFileViewProvider in the JetBrains Handlebars plugin (https://github.com/JetBrains/intellij-plugins),
// Copyright 2000-2025 JetBrains s.r.o. and contributors, Apache License 2.0. Modified for Nunjucks; see NOTICE.

package uk.steers.nunjucks.file

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.LanguageSubstitutors
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.ConfigurableTemplateLanguageFileViewProvider
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.psi.tree.IElementType
import uk.steers.nunjucks.NunjucksLanguage

class NunjucksFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    physical: Boolean,
    private val templateDataLanguage: Language = templateDataLanguage(manager, file),
) : MultiplePsiFilesPerDocumentFileViewProvider(manager, file, physical), ConfigurableTemplateLanguageFileViewProvider {

    override fun getBaseLanguage(): Language = NunjucksLanguage

    override fun getTemplateDataLanguage(): Language = templateDataLanguage

    override fun getLanguages(): Set<Language> = setOf(NunjucksLanguage, templateDataLanguage)

    override fun supportsIncrementalReparse(rootLanguage: Language): Boolean = false

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider =
        NunjucksFileViewProvider(manager, fileCopy, false, templateDataLanguage)

    override fun getContentElementType(language: Language): IElementType? =
        if (language.`is`(templateDataLanguage)) NunjucksTemplateData.TEMPLATE_DATA else null

    override fun createFile(lang: Language): PsiFile? = when {
        lang.`is`(templateDataLanguage) -> {
            val file = LanguageParserDefinitions.INSTANCE.forLanguage(lang)?.createFile(this)
            (file as? PsiFileImpl)?.setContentElementType(NunjucksTemplateData.TEMPLATE_DATA)
            file
        }
        lang.isKindOf(NunjucksLanguage) -> LanguageParserDefinitions.INSTANCE.forLanguage(NunjucksLanguage).createFile(this)
        else -> null
    }

    companion object {
        fun templateDataLanguage(manager: PsiManager, file: VirtualFile): Language {
            val mapped = TemplateDataLanguageMappings.getInstance(manager.project)?.getMapping(file) ?: HTMLLanguage.INSTANCE
            val substituted = LanguageSubstitutors.getInstance().substituteLanguage(mapped, file, manager.project)
            return if (TemplateDataLanguageMappings.getTemplateableLanguages().contains(substituted)) substituted else mapped
        }
    }
}

class NunjucksFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language?,
        manager: PsiManager,
        eventSystemEnabled: Boolean,
    ): FileViewProvider = NunjucksFileViewProvider(manager, file, eventSystemEnabled)
}
