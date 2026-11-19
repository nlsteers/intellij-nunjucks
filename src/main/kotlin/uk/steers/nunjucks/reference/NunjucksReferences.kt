package uk.steers.nunjucks.reference

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.util.PsiTreeUtil
import uk.steers.nunjucks.NunjucksIcons
import uk.steers.nunjucks.env.NunjucksEnvironment
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.psi.impl.identifier

private fun PsiElement.identifierRange(): TextRange {
    val identifier = node.findChildByType(NunjucksTypes.IDENTIFIER)?.psi ?: return TextRange(0, textLength)
    return TextRange.from(identifier.startOffsetInParent, identifier.textLength)
}

private fun PsiElement.renameIdentifier(newName: String): PsiElement {
    node.findChildByType(NunjucksTypes.IDENTIFIER)?.psi?.replace(NunjucksElementFactory.createIdentifier(project, newName))
    return this
}

/** The macro a call expression invokes, e.g. `govukButton(...)` or `forms.input(...)`. */
fun resolveCallee(callee: NjkExpression?): NjkMacroStatement? = when (callee) {
    is NjkReferenceExpr -> callee.reference?.resolve() as? NjkMacroStatement
    is NjkMemberExpr -> callee.reference?.resolve() as? NjkMacroStatement
    else -> null
}

class NunjucksVariableReference(element: NjkReferenceExpr) :
    PsiReferenceBase<NjkReferenceExpr>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? = ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, false)

    override fun handleElementRename(newElementName: String): PsiElement = element.renameIdentifier(newElementName)

    private companion object {
        val RESOLVER = ResolveCache.AbstractResolver<NunjucksVariableReference, PsiElement> { ref, _ ->
            NunjucksResolver.resolve(ref.element, ref.element.text)
        }
    }
}

/** `alias.macroName` where alias comes from `{% import "x" as alias %}`. */
class NunjucksMemberReference(element: NjkMemberExpr) :
    PsiReferenceBase<NjkMemberExpr>(element, element.identifierRange(), true) {

    override fun resolve(): PsiElement? {
        val file = importedFile() ?: return null
        return NunjucksResolver.findMacro(file, element.identifier?.text ?: return null)
    }

    fun importedFile() = (qualifierTarget()?.parent as? NjkImportTag)?.let { NunjucksResolver.templateFile(it) }

    private fun qualifierTarget(): NjkImportAlias? =
        (element.expression as? NjkReferenceExpr)?.reference?.resolve() as? NjkImportAlias

    override fun getVariants(): Array<Any> = importedFile()?.let { file ->
        NunjucksResolver.macros(file).mapNotNull { macro ->
            macro.name?.let { LookupElementBuilder.create(macro, it).withIcon(NunjucksIcons.FILE).withTypeText(file.name) }
        }.toTypedArray()
    } ?: emptyArray()

    override fun handleElementRename(newElementName: String): PsiElement = element.renameIdentifier(newElementName)
}

/** Navigates from a filter name to its `addFilter("name", ...)` registration. */
class NunjucksFilterReference(element: NjkFilterName) :
    PsiPolyVariantReferenceBase<NjkFilterName>(element, TextRange(0, element.textLength), true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        NunjucksEnvironment.getInstance(element.project).filterRegistrations(element.text)
            .map { PsiElementResolveResult(it) }.toTypedArray()
}

/** `macro(name=value)` → the macro's `name` parameter. */
class NunjucksKeywordArgumentReference(element: NjkKeywordName) :
    PsiReferenceBase<NjkKeywordName>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? = macro()?.macroTag?.parameterList?.parameterList?.firstOrNull { it.name == element.text }

    fun macro(): NjkMacroStatement? {
        val call = PsiTreeUtil.getParentOfType(element, NjkCallExpr::class.java) ?: return null
        return resolveCallee(call.expression)
    }

    override fun handleElementRename(newElementName: String): PsiElement = element.renameIdentifier(newElementName)
}

/** `{% from "x" import name %}` → the macro `name` in x. */
class NunjucksImportedNameReference(element: NjkImportedName) :
    PsiReferenceBase<NjkImportedName>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? = NunjucksResolver.resolveImportedMacro(element.parent as NjkImportSpec)

    override fun getVariants(): Array<Any> {
        val tag = PsiTreeUtil.getParentOfType(element, NjkFromImportTag::class.java) ?: return emptyArray()
        val file = NunjucksResolver.templateFile(tag) ?: return emptyArray()
        return NunjucksResolver.macros(file).mapNotNull { macro ->
            macro.name?.let { LookupElementBuilder.create(macro, it).withIcon(NunjucksIcons.FILE).withTypeText(file.name) }
        }.toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement = element.renameIdentifier(newElementName)
}

/** `{% endblock name %}` → the block it closes. */
class NunjucksEndBlockReference(element: NjkEndBlockName) :
    PsiReferenceBase<NjkEndBlockName>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? = PsiTreeUtil.getParentOfType(element, NjkBlockStatement::class.java)

    override fun handleElementRename(newElementName: String): PsiElement = element.renameIdentifier(newElementName)
}
