package uk.steers.nunjucks.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import uk.steers.nunjucks.NunjucksIcons
import uk.steers.nunjucks.psi.NjkBlockStatement
import uk.steers.nunjucks.psi.NjkImportAlias
import uk.steers.nunjucks.psi.NjkLiteralExpr
import uk.steers.nunjucks.psi.NjkMacroStatement
import uk.steers.nunjucks.psi.NjkNamedElement
import uk.steers.nunjucks.psi.NjkParameter
import uk.steers.nunjucks.psi.NjkTargetVariable
import uk.steers.nunjucks.psi.NunjucksElementFactory
import uk.steers.nunjucks.psi.NunjucksTypes
import uk.steers.nunjucks.reference.NunjucksTemplateReferences
import javax.swing.Icon

abstract class NjkNamedElementBase(node: ASTNode) : NunjucksPsiElementImpl(node), NjkNamedElement {
    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        nameIdentifier?.replace(NunjucksElementFactory.createIdentifier(project, name))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = name
        override fun getLocationString(): String? = containingFile?.name
        override fun getIcon(unused: Boolean): Icon = NunjucksIcons.FILE
    }
}

/** target_variable, parameter and import_alias: the IDENTIFIER is a direct child. */
abstract class NjkNamedElementMixin(node: ASTNode) : NjkNamedElementBase(node) {
    override fun getNameIdentifier(): PsiElement? = node.findChildByType(NunjucksTypes.IDENTIFIER)?.psi

    override fun getUseScope(): SearchScope {
        val enclosingScope = when (this) {
            is NjkParameter -> PsiTreeUtil.getParentOfType(this, NjkMacroStatement::class.java, uk.steers.nunjucks.psi.NjkCallStatement::class.java)
            is NjkTargetVariable -> PsiTreeUtil.getParentOfType(this, uk.steers.nunjucks.psi.NjkForStatement::class.java)
            is NjkImportAlias -> containingFile
            else -> null
        }
        return enclosingScope?.let { LocalSearchScope(it) } ?: super.getUseScope()
    }
}

abstract class NjkMacroMixin(node: ASTNode) : NjkNamedElementBase(node), NjkMacroStatement {
    override fun getNameIdentifier(): PsiElement? =
        macroTag.macroName?.node?.findChildByType(NunjucksTypes.IDENTIFIER)?.psi
}

abstract class NjkBlockMixin(node: ASTNode) : NjkNamedElementBase(node), NjkBlockStatement {
    override fun getNameIdentifier(): PsiElement? =
        blockTag.blockName?.node?.findChildByType(NunjucksTypes.IDENTIFIER)?.psi
}

abstract class NjkLiteralMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkLiteralExpr {
    val isString: Boolean get() = node.firstChildNode?.elementType == NunjucksTypes.STRING

    /** The unquoted value of a string literal, or null for non-strings. */
    val stringValue: String? get() = if (isString) valueRange.substring(text) else null

    /** The range of the string's content inside its quotes. */
    val valueRange: TextRange
        get() {
            val text = text
            if (text.length < 2) return TextRange(text.length.coerceAtMost(1), text.length)
            val end = if (text.last() == text.first()) text.length - 1 else text.length
            return TextRange(1, end)
        }

    override fun getReferences(): Array<PsiReference> = NunjucksTemplateReferences.forLiteral(this)

    override fun getReference(): PsiReference? = references.lastOrNull()
}
