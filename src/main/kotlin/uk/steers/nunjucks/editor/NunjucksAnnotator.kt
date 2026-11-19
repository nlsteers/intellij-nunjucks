package uk.steers.nunjucks.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.highlighting.NunjucksHighlighterColours as C
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.psi.impl.identifier
import uk.steers.nunjucks.reference.resolveCallee

class NunjucksAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val type = element.elementType
        when {
            type in NunjucksTokenSets.SOFT_KEYWORDS -> holder.highlight(element, C.KEYWORD)
            type == NunjucksTypes.STRING -> checkString(element, holder)
            type == NunjucksTypes.COMMENT && !element.text.endsWith("#}") ->
                holder.newAnnotation(HighlightSeverity.ERROR, "Unclosed comment").range(element).create()
        }
        when (element) {
            is NjkFilterName -> holder.highlight(element, C.FILTER)
            is NjkMacroName -> holder.highlight(element, C.MACRO_DECLARATION)
            is NjkParameter -> element.nameIdentifier?.let { holder.highlight(it, C.PARAMETER) }
            is NjkBlockName, is NjkEndBlockName -> holder.highlight(element, C.BLOCK_NAME)
            is NjkDictKey -> holder.highlight(element, C.DICT_KEY)
            is NjkKeywordName -> holder.highlight(element, C.KEYWORD_ARGUMENT)
            is NjkIsExpr -> testName(element)?.let { holder.highlight(it, C.TEST) }
            is NjkCallExpr -> highlightMacroCall(element, holder)
            is NjkStatement -> checkClosed(element, holder)
            is NjkOrphanTag -> holder.newAnnotation(HighlightSeverity.ERROR, "Unexpected ${element.text.trim()}: no open block to close").range(element).create()
        }
    }

    private fun highlightMacroCall(call: NjkCallExpr, holder: AnnotationHolder) {
        val callee = call.expression
        val name = when (callee) {
            is NjkReferenceExpr -> callee.identifier
            is NjkMemberExpr -> callee.identifier
            else -> null
        } ?: return
        if (resolveCallee(callee) != null) holder.highlight(name, C.MACRO_CALL)
    }

    private fun checkClosed(statement: NjkStatement, holder: AnnotationHolder) {
        val block = statement.block ?: return
        if (!block.isClosed) {
            holder.newAnnotation(HighlightSeverity.ERROR, "${block.openTagText} is not closed: expected ${block.expectedEndTag}")
                .range(block.openRange)
                .create()
        }
    }

    private fun checkString(element: PsiElement, holder: AnnotationHolder) {
        if (!isTerminated(element.text)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Unterminated string").range(element).create()
        }
    }

    private fun isTerminated(text: String): Boolean {
        if (text.length < 2 || text.last() != text.first()) return false
        val backslashes = text.dropLast(1).takeLastWhile { it == '\\' }.length
        return backslashes % 2 == 0
    }

    private fun AnnotationHolder.highlight(element: PsiElement, key: TextAttributesKey) =
        newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(key).create()

    companion object {
        /** `x is divisibleby(3)` / `x is not defined` → the test name element. */
        fun testName(expr: NjkIsExpr): PsiElement? = when (val test = expr.expressionList.getOrNull(1)) {
            is NjkReferenceExpr -> test.identifier
            is NjkCallExpr -> (test.expression as? NjkReferenceExpr)?.identifier
            else -> null
        }
    }
}

/** Hides the parser's "end tag expected" errors; the annotator reports the unclosed opening tag instead. */
class NunjucksErrorFilter : com.intellij.codeInsight.highlighting.HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: com.intellij.psi.PsiErrorElement): Boolean {
        if (element.language != uk.steers.nunjucks.NunjucksLanguage) return true
        val parent = element.parent
        val statement = (if (parent is NjkBody) parent.parent else parent) as? NjkStatement ?: return true
        val block = statement.block ?: return true
        val isTrailing = com.intellij.psi.util.PsiTreeUtil.skipWhitespacesForward(element) == null
        return !isTrailing || block.isClosed
    }
}
