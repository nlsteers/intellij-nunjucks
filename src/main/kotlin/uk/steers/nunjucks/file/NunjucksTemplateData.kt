// Derived from HbFileViewProvider in the JetBrains Handlebars plugin (https://github.com/JetBrains/intellij-plugins),
// Copyright 2000-2025 JetBrains s.r.o. and contributors, Apache License 2.0. Modified for Nunjucks; see NOTICE.

package uk.steers.nunjucks.file

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.OuterLanguageElementType
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataModifications
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.psi.NunjucksTypes

object NunjucksTemplateData {
    @JvmField
    val OUTER_ELEMENT_TYPE = OuterLanguageElementType("NJK_FRAGMENT", NunjucksLanguage)

    @JvmField
    val TEMPLATE_DATA: TemplateDataElementType =
        object : TemplateDataElementType("NJK_TEMPLATE_DATA", NunjucksLanguage, NunjucksTypes.TEMPLATE_TEXT, OUTER_ELEMENT_TYPE) {
            // `<a href={{ url }}>` – give the data language an attribute value so it doesn't report an error
            override fun appendCurrentTemplateToken(tokenEndOffset: Int, tokenText: CharSequence): TemplateDataModifications =
                if (StringUtil.endsWithChar(tokenText, '=')) {
                    TemplateDataModifications.fromRangeToRemove(tokenEndOffset, "\"\"")
                } else {
                    super.appendCurrentTemplateToken(tokenEndOffset, tokenText)
                }
        }
}
