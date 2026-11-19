package uk.steers.nunjucks.navigation

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.psi.NjkArgumentList
import uk.steers.nunjucks.psi.NjkCallExpr
import uk.steers.nunjucks.psi.NjkMacroStatement
import uk.steers.nunjucks.psi.NunjucksTypes
import uk.steers.nunjucks.reference.resolveCallee

/** Parameter hints for macro calls: `field(|)` shows `name, value="", type="text"`. */
class NunjucksParameterInfoHandler : ParameterInfoHandler<NjkArgumentList, NjkMacroStatement> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): NjkArgumentList? {
        val arguments = findArguments(context.file, context.offset) ?: return null
        val macro = resolveCallee((arguments.parent as NjkCallExpr).expression) ?: return null
        context.itemsToShow = arrayOf(macro)
        return arguments
    }

    override fun showParameterInfo(element: NjkArgumentList, context: CreateParameterInfoContext) =
        context.showHint(element, element.textRange.startOffset, this)

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): NjkArgumentList? =
        findArguments(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: NjkArgumentList, context: UpdateParameterInfoContext) {
        val commas = parameterOwner.node.getChildren(null)
            .count { it.elementType == NunjucksTypes.COMMA && it.startOffset < context.offset }
        context.setCurrentParameter(commas)
    }

    override fun updateUI(macro: NjkMacroStatement, context: ParameterInfoUIContext) {
        val parameters = macro.macroTag.parameterList?.parameterList.orEmpty().map { it.text }
        if (parameters.isEmpty()) {
            context.setupUIComponentPresentation("<no parameters>", -1, -1, false, false, false, context.defaultParameterColor)
            return
        }
        val index = context.currentParameterIndex
        var start = -1
        var end = -1
        val text = buildString {
            parameters.forEachIndexed { i, parameter ->
                if (i > 0) append(", ")
                if (i == index) start = length
                append(parameter)
                if (i == index) end = length
            }
        }
        context.setupUIComponentPresentation(text, start, end, false, false, false, context.defaultParameterColor)
    }

    private fun findArguments(file: PsiFile, offset: Int): NjkArgumentList? {
        val psi = file.viewProvider.getPsi(NunjucksLanguage) ?: return null
        val element = psi.findElementAt(offset) ?: psi.findElementAt(offset - 1) ?: return null
        var arguments = PsiTreeUtil.getParentOfType(element, NjkArgumentList::class.java, false)
        while (arguments != null && arguments.parent !is NjkCallExpr) {
            arguments = PsiTreeUtil.getParentOfType(arguments, NjkArgumentList::class.java)
        }
        return arguments
    }
}
