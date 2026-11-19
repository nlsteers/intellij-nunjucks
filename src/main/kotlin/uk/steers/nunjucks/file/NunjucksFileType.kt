package uk.steers.nunjucks.file

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.TemplateLanguageFileType
import uk.steers.nunjucks.NunjucksIcons
import uk.steers.nunjucks.NunjucksLanguage
import javax.swing.Icon

object NunjucksFileType : LanguageFileType(NunjucksLanguage), TemplateLanguageFileType {
    override fun getName(): String = "Nunjucks"

    override fun getDescription(): String = "Nunjucks template"

    override fun getDefaultExtension(): String = "njk"

    override fun getIcon(): Icon = NunjucksIcons.FILE
}
