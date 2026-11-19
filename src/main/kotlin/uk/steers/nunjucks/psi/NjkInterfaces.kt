package uk.steers.nunjucks.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner

interface NjkStatement : PsiElement

/** A variable, parameter, macro, block or import alias declared in a template. */
interface NjkNamedElement : PsiNameIdentifierOwner

/** extends / include / import / from-import: tags whose first expression names another template. */
interface NjkTemplateReferencingTag : PsiElement
