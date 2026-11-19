package uk.steers.nunjucks.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.TemplateLanguageStructureViewBuilder
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import uk.steers.nunjucks.NunjucksIcons
import uk.steers.nunjucks.file.NunjucksFile
import uk.steers.nunjucks.psi.*
import javax.swing.Icon

class NunjucksStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile) =
        TemplateLanguageStructureViewBuilder.create(psiFile) { file, editor -> NunjucksStructureViewModel(file, editor) }
}

class NunjucksStructureViewModel(file: PsiFile, editor: Editor?) :
    StructureViewModelBase(file, editor, NunjucksStructureElement(file)), StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(NjkBlockStatement::class.java, NjkMacroStatement::class.java, NjkSetStatement::class.java,
            NjkImportTag::class.java, NjkFromImportTag::class.java, NjkExtendsTag::class.java, NjkIncludeTag::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement) = element.value !is NjkBlockStatement && element.value !is PsiFile
}

/** Shows extends/imports/includes, blocks (nested), macros and top-level sets. */
class NunjucksStructureElement(element: PsiElement) : PsiTreeElementBase<PsiElement>(element) {
    override fun getChildrenBase(): Collection<StructureViewTreeElement> {
        val root = element ?: return emptyList()
        val scope = when (root) {
            is NunjucksFile -> root
            is NjkBlockStatement -> root.body
            else -> null
        } ?: return emptyList()
        val children = mutableListOf<StructureViewTreeElement>()
        collect(scope, children)
        return children
    }

    private fun collect(element: PsiElement, into: MutableList<StructureViewTreeElement>) {
        var child = element.firstChild
        while (child != null) {
            when (child) {
                is NjkBlockStatement, is NjkMacroStatement, is NjkImportTag, is NjkFromImportTag,
                is NjkExtendsTag, is NjkIncludeTag -> into.add(NunjucksStructureElement(child))
                is NjkSetStatement -> if (element is NunjucksFile || element is NjkBody) into.add(NunjucksStructureElement(child))
                is NjkForStatement, is NjkCallStatement -> Unit
                else -> collect(child, into)
            }
            child = child.nextSibling
        }
    }

    override fun getPresentableText(): String? = when (val e = element) {
        is NunjucksFile -> e.name
        is NjkBlockStatement -> "block ${e.name.orEmpty()}"
        is NjkMacroStatement -> e.name + (e.macroTag.parameterList?.text ?: "()")
        is NjkSetStatement -> "set " + e.targetVariableList.joinToString(", ") { it.text }
        is NjkTemplateReferencingTag -> e.text.removePrefix("{%").removeSuffix("%}").trim('-', ' ')
        else -> e?.text
    }

    override fun getIcon(open: Boolean): Icon = when (element) {
        is NjkMacroStatement -> AllIcons.Nodes.Function
        is NjkBlockStatement -> AllIcons.Nodes.Tag
        is NjkSetStatement -> AllIcons.Nodes.Variable
        is NjkTemplateReferencingTag -> AllIcons.Nodes.Include
        else -> NunjucksIcons.FILE
    }
}
