package uk.steers.nunjucks

import com.intellij.lang.Language
import com.intellij.psi.templateLanguages.TemplateLanguage

object NunjucksLanguage : Language("Nunjucks"), TemplateLanguage {
    private fun readResolve(): Any = NunjucksLanguage

    override fun getDisplayName(): String = "Nunjucks"
}
