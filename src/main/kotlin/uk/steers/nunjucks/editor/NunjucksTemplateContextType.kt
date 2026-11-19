package uk.steers.nunjucks.editor

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import uk.steers.nunjucks.file.NunjucksFileViewProvider

class NunjucksTemplateContextType : TemplateContextType("Nunjucks") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean =
        templateActionContext.file.viewProvider is NunjucksFileViewProvider
}
