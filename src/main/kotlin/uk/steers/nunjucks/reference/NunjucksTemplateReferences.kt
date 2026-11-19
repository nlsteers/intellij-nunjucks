package uk.steers.nunjucks.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.util.PsiTreeUtil
import uk.steers.nunjucks.psi.NjkExpression
import uk.steers.nunjucks.psi.NjkLiteralExpr
import uk.steers.nunjucks.psi.NjkTemplateReferencingTag
import uk.steers.nunjucks.psi.NunjucksElementFactory
import uk.steers.nunjucks.psi.impl.NjkLiteralMixin

object NunjucksTemplateReferences {
    /** The template path literal of an extends/include/import/from tag, if it is a plain string. */
    fun templatePathLiteral(tag: NjkTemplateReferencingTag): NjkLiteralMixin? =
        PsiTreeUtil.getChildOfType(tag, NjkExpression::class.java) as? NjkLiteralMixin

    fun isTemplatePath(literal: NjkLiteralMixin): Boolean {
        val tag = literal.parent as? NjkTemplateReferencingTag ?: return false
        return literal.isString && templatePathLiteral(tag) == literal
    }

    fun forLiteral(literal: NjkLiteralMixin): Array<PsiReference> {
        if (!isTemplatePath(literal)) return PsiReference.EMPTY_ARRAY
        val value = literal.stringValue ?: return PsiReference.EMPTY_ARRAY
        return NunjucksTemplateFileReferenceSet(value, literal).allReferences.map { it as PsiReference }.toTypedArray()
    }
}

class NunjucksTemplateFileReferenceSet(path: String, private val literal: NjkLiteralMixin) :
    FileReferenceSet(path, literal, literal.valueRange.startOffset, null, true) {

    override fun isSoft(): Boolean = true

    override fun isAbsolutePathReference(): Boolean = false

    override fun computeDefaultContexts(): Collection<PsiFileSystemItem> {
        val file = literal.containingFile.originalFile
        val manager = PsiManager.getInstance(literal.project)
        if (NunjucksTemplateResolver.isRelative(pathString)) {
            return listOfNotNull(file.containingDirectory)
        }
        val roots = NunjucksTemplateResolver.searchRoots(literal.project).toMutableList()
        NunjucksTemplateResolver.resolve(file, pathString)?.let { resolved ->
            NunjucksTemplateResolver.rootOf(resolved, pathString)?.let { roots.add(0, it) }
        }
        return roots.distinct().mapNotNull { manager.findDirectory(it) }
    }
}

class NunjucksLiteralManipulator : AbstractElementManipulator<NjkLiteralExpr>() {
    override fun handleContentChange(element: NjkLiteralExpr, range: TextRange, newContent: String): NjkLiteralExpr {
        val newText = range.replace(element.text, newContent)
        element.firstChild.replace(NunjucksElementFactory.createString(element.project, newText))
        return element
    }

    override fun getRangeInElement(element: NjkLiteralExpr): TextRange =
        (element as? NjkLiteralMixin)?.takeIf { it.isString }?.valueRange ?: TextRange(0, element.textLength)
}
