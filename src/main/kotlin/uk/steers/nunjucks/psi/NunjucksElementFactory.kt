package uk.steers.nunjucks.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.file.NunjucksFileType

object NunjucksElementFactory {
    fun createFile(project: Project, text: String): PsiFile =
        PsiFileFactory.getInstance(project).createFileFromText("dummy.njk", NunjucksFileType, text)
            .viewProvider.getPsi(NunjucksLanguage)!!

    fun createIdentifier(project: Project, name: String): PsiElement = createLeaf(project, "{{ $name }}", NunjucksTypes.IDENTIFIER)

    fun createString(project: Project, quotedText: String): PsiElement = createLeaf(project, "{{ $quotedText }}", NunjucksTypes.STRING)

    private fun createLeaf(project: Project, text: String, type: com.intellij.psi.tree.IElementType): PsiElement =
        PsiTreeUtil.collectElements(createFile(project, text)) { it.elementType == type }.first()
}
