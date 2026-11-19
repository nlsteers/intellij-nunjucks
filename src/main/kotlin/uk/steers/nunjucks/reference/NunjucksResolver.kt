package uk.steers.nunjucks.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.file.NunjucksFile
import uk.steers.nunjucks.psi.*

enum class DeclarationKind { VARIABLE, LOOP_VARIABLE, PARAMETER, MACRO, IMPORT_ALIAS, IMPORTED_MACRO }

/** A name visible at some point in a template. [element] is the declaring PSI (for IMPORTED_MACRO, the import_spec). */
data class Declaration(val name: String, val element: PsiElement, val kind: DeclarationKind)

object NunjucksResolver {
    fun nunjucksFile(element: PsiElement): NunjucksFile? = nunjucksFileOf(element.containingFile)

    fun nunjucksFileOf(file: PsiFile?): NunjucksFile? = file?.viewProvider?.getPsi(NunjucksLanguage) as? NunjucksFile

    fun macros(file: NunjucksFile): List<NjkMacroStatement> = CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(
            PsiTreeUtil.findChildrenOfType(file, NjkMacroStatement::class.java).toList(),
            PsiModificationTracker.MODIFICATION_COUNT,
        )
    }

    fun findMacro(file: NunjucksFile, name: String): NjkMacroStatement? = macros(file).firstOrNull { it.name == name }

    fun templateFile(tag: NjkTemplateReferencingTag): NunjucksFile? {
        val path = NunjucksTemplateReferences.templatePathLiteral(tag)?.stringValue ?: return null
        return NunjucksTemplateResolver.resolveNunjucksFile(tag.containingFile, path)
    }

    /** The macro an imported name in `{% from x import name %}` refers to. */
    fun resolveImportedMacro(spec: NjkImportSpec): NjkMacroStatement? {
        val tag = spec.parent as? NjkFromImportTag ?: return null
        val file = templateFile(tag) ?: return null
        return findMacro(file, spec.importedName.text)
    }

    fun importSpecName(spec: NjkImportSpec): String = spec.importAlias?.name ?: spec.importedName.text

    /** Declarations visible at [place], nearest first. Names may repeat; the first one wins. */
    fun visibleDeclarations(place: PsiElement): List<Declaration> {
        val result = mutableListOf<Declaration>()
        val offset = place.textRange.startOffset
        var child: PsiElement = place
        var parent: PsiElement? = place.parent

        while (parent != null && parent !is PsiFile) {
            if (child is NjkBody) {
                when (parent) {
                    is NjkForStatement -> {
                        addSets(parent.body, offset, result)
                        parent.forTag.targetVariableList.forEach { result.add(it.declaration(DeclarationKind.LOOP_VARIABLE)) }
                    }
                    is NjkMacroStatement -> {
                        addSets(parent.body, offset, result)
                        parent.macroTag.parameterList?.parameterList?.forEach { result.add(it.declaration(DeclarationKind.PARAMETER)) }
                    }
                    is NjkCallStatement -> {
                        addSets(parent.body, offset, result)
                        parent.callTag.parameterList?.parameterList?.forEach { result.add(it.declaration(DeclarationKind.PARAMETER)) }
                    }
                }
            } else if (parent is NjkParameterList && parent.parent is NjkMacroTag) {
                parent.parameterList.filter { it.textRange.endOffset <= offset }
                    .forEach { result.add(it.declaration(DeclarationKind.PARAMETER)) }
            }
            child = parent
            parent = parent.parent
        }

        val file = nunjucksFile(place) ?: return result
        addSets(file, offset, result)
        fileDeclarations(file).forEach { result.add(it) }
        return result
    }

    fun resolve(place: PsiElement, name: String): PsiElement? {
        val declaration = visibleDeclarations(place).firstOrNull { it.name == name } ?: return null
        val spec = declaration.element as? NjkImportSpec ?: return declaration.element
        return resolveImportedMacro(spec) ?: spec.importedName
    }

    /** Macros and imports – visible everywhere in the file regardless of position. */
    fun fileDeclarations(file: NunjucksFile): List<Declaration> = CachedValuesManager.getCachedValue(file) {
        val declarations = mutableListOf<Declaration>()
        macros(file).forEach { macro -> macro.name?.let { declarations.add(Declaration(it, macro, DeclarationKind.MACRO)) } }
        PsiTreeUtil.findChildrenOfType(file, NjkImportTag::class.java).forEach { tag ->
            tag.importAlias?.let { declarations.add(it.declaration(DeclarationKind.IMPORT_ALIAS)) }
        }
        PsiTreeUtil.findChildrenOfType(file, NjkImportSpec::class.java).forEach { spec ->
            declarations.add(Declaration(importSpecName(spec), spec, DeclarationKind.IMPORTED_MACRO))
        }
        CachedValueProvider.Result.create(declarations, PsiModificationTracker.MODIFICATION_COUNT)
    }

    /** Sets declared in [scope] but not inside a nested for/macro/call scope: nearest preceding first, then following. */
    private fun addSets(scope: PsiElement?, offset: Int, result: MutableList<Declaration>) {
        if (scope == null) return
        val sets = mutableListOf<NjkTargetVariable>()
        collectSets(scope, sets)
        val (before, after) = sets.partition { it.textRange.endOffset <= offset }
        before.asReversed().forEach { result.add(it.declaration(DeclarationKind.VARIABLE)) }
        after.forEach { result.add(it.declaration(DeclarationKind.VARIABLE)) }
    }

    private fun collectSets(element: PsiElement, into: MutableList<NjkTargetVariable>) {
        var child = element.firstChild
        while (child != null) {
            when (child) {
                is NjkSetStatement -> {
                    into.addAll(child.targetVariableList)
                    child.body?.let { collectSets(it, into) }
                }
                is NjkForStatement, is NjkMacroStatement, is NjkCallStatement -> Unit
                else -> collectSets(child, into)
            }
            child = child.nextSibling
        }
    }

    fun isScopeStatement(element: PsiElement) =
        element is NjkForStatement || element is NjkMacroStatement || element is NjkCallStatement

    private fun NjkNamedElement.declaration(kind: DeclarationKind) = Declaration(name.orEmpty(), this, kind)
}
