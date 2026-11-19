package uk.steers.nunjucks.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.openapi.util.text.StringUtil
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.reference.*

val NjkReferenceExpr.identifier: PsiElement get() = node.findChildByType(NunjucksTypes.IDENTIFIER)!!.psi

val NjkMemberExpr.identifier: PsiElement? get() = node.findChildByType(NunjucksTypes.IDENTIFIER)?.psi

/** The key as nunjucks sees it: bare identifiers and strings both name a property. */
val NjkDictKey.keyName: String
    get() = if (firstChild.node.elementType == NunjucksTypes.STRING) StringUtil.unquoteString(text) else text

abstract class NjkReferenceExprMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkReferenceExpr {
    override fun getReference(): PsiReference = NunjucksVariableReference(this)
}

abstract class NjkMemberExprMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkMemberExpr {
    override fun getReference(): PsiReference? = if (identifier != null) NunjucksMemberReference(this) else null
}

abstract class NjkFilterNameMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkFilterName {
    override fun getReference(): PsiReference = NunjucksFilterReference(this)
}

abstract class NjkKeywordNameMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkKeywordName {
    override fun getReference(): PsiReference = NunjucksKeywordArgumentReference(this)
}

abstract class NjkImportedNameMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkImportedName {
    override fun getReference(): PsiReference = NunjucksImportedNameReference(this)
}

abstract class NjkEndBlockNameMixin(node: ASTNode) : NunjucksPsiElementImpl(node), NjkEndBlockName {
    override fun getReference(): PsiReference = NunjucksEndBlockReference(this)
}
