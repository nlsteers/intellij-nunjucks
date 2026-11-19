package uk.steers.nunjucks.navigation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_END
import com.intellij.lang.documentation.DocumentationMarkup.CONTENT_START
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_END
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_START
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.editor.NunjucksAnnotator
import uk.steers.nunjucks.env.Builtin
import uk.steers.nunjucks.env.NunjucksBuiltins
import uk.steers.nunjucks.govuk.MacroOptions
import uk.steers.nunjucks.psi.*

class NunjucksDocumentationProvider : AbstractDocumentationProvider() {
    /** Built-ins and GOV.UK options have no PSI of their own; document the element under the caret instead. */
    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
        val parent = contextElement?.parent ?: return null
        return when {
            parent is NjkFilterName && parent.reference?.resolve() == null -> parent
            parent is NjkDictKey && MacroOptions.optionFor(parent) != null -> parent
            parent is NjkReferenceExpr && builtinFor(parent) != null -> parent
            else -> null
        }
    }

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? = when (element) {
        is NjkFilterName -> NunjucksBuiltins.FILTERS[element.text]?.let { builtinDoc("filter", it) }
        is NjkReferenceExpr -> builtinFor(element)?.let { (kind, builtin) -> builtinDoc(kind, builtin) }
        is NjkDictKey -> MacroOptions.optionFor(element)?.let { option ->
            definition("${option.name}: ${option.type}" + if (option.required) " (required)" else "") +
                content(StringUtil.escapeXmlEntities(option.description) + (option.deprecated?.let { "<p><b>Deprecated</b> in $it</p>" } ?: ""))
        }
        is NjkMacroStatement -> definition("macro ${element.name}${element.macroTag.parameterList?.text ?: "()"}") +
            (precedingComment(element)?.let { content(StringUtil.escapeXmlEntities(it)) } ?: "") +
            content("<i>${element.containingFile.name}</i>")
        is NjkParameter -> definition("parameter ${element.text}")
        is NjkBlockStatement -> definition("block ${element.name}") + content("<i>${element.containingFile.name}</i>")
        else -> null
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? = generateDoc(element, originalElement)

    private fun builtinFor(reference: NjkReferenceExpr): Pair<String, Builtin>? {
        val isExpr = PsiTreeUtil.getParentOfType(reference, NjkIsExpr::class.java)
        if (isExpr != null && NunjucksAnnotator.testName(isExpr) == reference.firstChild) {
            return NunjucksBuiltins.TESTS[reference.text]?.let { "test" to it }
        }
        if (reference.reference?.resolve() != null) return null
        return NunjucksBuiltins.GLOBALS[reference.text]?.let { "global function" to it }
    }

    private fun builtinDoc(kind: String, builtin: Builtin): String {
        val description = builtin.aliasOf?.let { alias ->
            (NunjucksBuiltins.FILTERS[alias] ?: NunjucksBuiltins.TESTS[alias])?.description?.let { "Alias of <code>$alias</code>. $it" }
        } ?: builtin.description
        return definition("$kind ${builtin.name}${builtin.signature}") + content(StringUtil.escapeXmlEntities(description))
    }

    /** A `{# ... #}` comment directly above a macro documents it. */
    private fun precedingComment(macro: NjkMacroStatement): String? {
        var previous = macro.prevSibling
        while (previous is PsiWhiteSpace) previous = previous.prevSibling
        if (previous.elementType != NunjucksTypes.COMMENT) return null
        return previous!!.text.removePrefix("{#").removeSuffix("#}").trimIndent().trim()
    }

    private fun definition(text: String) = DEFINITION_START + StringUtil.escapeXmlEntities(text) + DEFINITION_END

    private fun content(html: String) = CONTENT_START + html + CONTENT_END
}
