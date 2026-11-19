package uk.steers.nunjucks.navigation

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import uk.steers.nunjucks.lexer.NunjucksLexer
import uk.steers.nunjucks.psi.*

class NunjucksFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        NunjucksLexer(),
        TokenSet.create(NunjucksTypes.IDENTIFIER),
        NunjucksTokenSets.COMMENTS,
        TokenSet.create(NunjucksTypes.STRING, NunjucksTypes.TEMPLATE_TEXT),
    )

    override fun canFindUsagesFor(element: PsiElement): Boolean = element is NjkNamedElement

    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is NjkMacroStatement -> "macro"
        is NjkBlockStatement -> "block"
        is NjkParameter -> "parameter"
        is NjkImportAlias -> "import alias"
        is NjkTargetVariable -> "variable"
        else -> "symbol"
    }

    override fun getDescriptiveName(element: PsiElement): String = (element as? NjkNamedElement)?.name.orEmpty()

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}

class NunjucksNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean = name in RESERVED

    override fun isIdentifier(name: String, project: Project?): Boolean = IDENTIFIER.matches(name) && !isKeyword(name, project)

    private companion object {
        val IDENTIFIER = Regex("""[\p{L}_$][\p{L}\p{N}_$]*""")
        val RESERVED = setOf("and", "or", "not", "in", "is", "if", "else", "true", "false", "none", "null")
    }
}

class NunjucksRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean = element is NjkNamedElement
}
