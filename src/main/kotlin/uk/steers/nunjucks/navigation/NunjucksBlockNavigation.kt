package uk.steers.nunjucks.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import uk.steers.nunjucks.file.NunjucksFile
import uk.steers.nunjucks.psi.NjkBlockName
import uk.steers.nunjucks.psi.NjkBlockStatement
import uk.steers.nunjucks.psi.NjkExtendsTag
import uk.steers.nunjucks.reference.NunjucksResolver

object NunjucksBlockInheritance {
    /** The block of the same name in the template this file extends (searching up the chain). */
    fun overriddenBlock(block: NjkBlockStatement): NjkBlockStatement? {
        val name = block.name ?: return null
        val visited = mutableSetOf<NunjucksFile>()
        var file = NunjucksResolver.nunjucksFile(block)
        while (file != null && visited.add(file)) {
            val parent = PsiTreeUtil.findChildOfType(file, NjkExtendsTag::class.java)?.let { NunjucksResolver.templateFile(it) } ?: return null
            PsiTreeUtil.findChildrenOfType(parent, NjkBlockStatement::class.java).firstOrNull { it.name == name }?.let { return it }
            file = parent
        }
        return null
    }
}

/** Gutter icon on `{% block name %}` when it overrides a block of the parent template. */
class NunjucksBlockLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        val name = element as? NjkBlockName ?: return
        val block = name.parent?.parent as? NjkBlockStatement ?: return
        val overridden = NunjucksBlockInheritance.overriddenBlock(block) ?: return
        val identifier = name.firstChild ?: return
        result.add(
            NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
                .setTargets(overridden)
                .setTooltipText("Overrides block '${block.name}' in ${overridden.containingFile.name}")
                .createLineMarkerInfo(identifier),
        )
    }
}

/** Ctrl+click on a block name jumps to the block it overrides. */
class NunjucksGotoBlockHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val name = sourceElement?.parent as? NjkBlockName ?: return null
        val block = name.parent?.parent as? NjkBlockStatement ?: return null
        return NunjucksBlockInheritance.overriddenBlock(block)?.let { arrayOf(it) }
    }
}
