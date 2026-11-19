package uk.steers.nunjucks.inspections

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.editor.NunjucksAnnotator
import uk.steers.nunjucks.env.NunjucksEnvironment
import uk.steers.nunjucks.govuk.MacroOptions
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.psi.impl.keyName
import uk.steers.nunjucks.reference.NunjucksMemberReference
import uk.steers.nunjucks.reference.NunjucksResolver
import uk.steers.nunjucks.reference.NunjucksTemplateReferences
import uk.steers.nunjucks.reference.NunjucksTemplateResolver
import uk.steers.nunjucks.reference.resolveCallee
import uk.steers.nunjucks.settings.NunjucksSettings

/** Visits every element of the Nunjucks tree; subclasses override [visit]. */
abstract class NunjucksInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) = visit(element, holder)
    }

    abstract fun visit(element: PsiElement, holder: ProblemsHolder)
}

class NunjucksUnknownFilterInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val filter = element as? NjkFilterName ?: return
        val name = filter.text
        if (!NunjucksEnvironment.getInstance(element.project).isKnownFilter(name)) {
            holder.registerProblem(filter, "Unknown filter '$name'", AddKnownNameFix(name, KnownNameKind.FILTER))
        }
    }
}

class NunjucksUnknownTestInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val isExpr = element as? NjkIsExpr ?: return
        val test = NunjucksAnnotator.testName(isExpr) ?: return
        val name = test.text
        if (!NunjucksEnvironment.getInstance(element.project).isKnownTest(name)) {
            holder.registerProblem(test, "Unknown test '$name'", AddKnownNameFix(name, KnownNameKind.TEST))
        }
    }
}

class NunjucksUnresolvedTemplateInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val tag = element as? NjkTemplateReferencingTag ?: return
        if (tag is NjkIncludeTag && tag.node.findChildByType(NunjucksTypes.MISSING) != null) return
        val literal = NunjucksTemplateReferences.templatePathLiteral(tag) ?: return
        val path = literal.stringValue?.takeIf { it.isNotBlank() } ?: return
        if (NunjucksTemplateResolver.resolve(tag.containingFile, path) == null) {
            holder.registerProblem(literal, literal.valueRange, "Cannot resolve template '$path'")
        }
    }
}

class NunjucksUnresolvedMacroInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        when (element) {
            is NjkImportedName -> {
                val tag = PsiTreeUtil.getParentOfType(element, NjkFromImportTag::class.java) ?: return
                val file = NunjucksResolver.templateFile(tag) ?: return
                if (element.reference?.resolve() == null) {
                    holder.registerProblem(element, "'${element.text}' is not a macro in ${file.name}", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                }
            }
            is NjkMemberExpr -> {
                val reference = element.reference as? NunjucksMemberReference ?: return
                val file = reference.importedFile() ?: return
                if (reference.resolve() == null) {
                    holder.registerProblem(element, "'${element.lastChild.text}' is not a macro in ${file.name}", ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, reference.rangeInElement)
                }
            }
        }
    }
}

class NunjucksUnusedImportInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        when (element) {
            is NjkImportTag -> element.importAlias?.let { alias -> if (isUnused(alias, alias.name)) holder.unused(element, "Unused import '${alias.name}'") }
            is NjkImportSpec -> {
                val name = NunjucksResolver.importSpecName(element)
                if (isUnused(element, name)) holder.unused(element, "Unused import '$name'")
            }
        }
    }

    private fun isUnused(declaration: PsiElement, name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        val file = NunjucksResolver.nunjucksFile(declaration) ?: return false
        return PsiTreeUtil.findChildrenOfType(file, NjkReferenceExpr::class.java).none { it.text == name }
    }

    private fun ProblemsHolder.unused(element: PsiElement, message: String) =
        registerProblem(element, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
}

/**
 * Variables set inside a macro, loop or call body that are never read there. Top-level sets are skipped:
 * parent and included templates can read them.
 */
class NunjucksUnusedVariableInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val target = element as? NjkTargetVariable ?: return
        val set = target.parent as? NjkSetStatement ?: return
        val scope = PsiTreeUtil.getParentOfType(set, NjkMacroStatement::class.java, NjkForStatement::class.java, NjkCallStatement::class.java) ?: return
        if (PsiTreeUtil.findChildOfType(scope, NjkIncludeTag::class.java) != null) return
        if (ReferencesSearch.search(target, LocalSearchScope(scope)).findFirst() == null) {
            holder.registerProblem(target, "Variable '${target.name}' is never used", ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
    }
}

class NunjucksMismatchedEndTagInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val statement = element as? NjkStatement ?: return
        val block = statement.block ?: return
        val endTag = block.endTag ?: return
        val endKeyword = NunjucksBlock.tagKeyword(endTag) ?: return
        if (endKeyword.text != block.expectedEnd) {
            holder.registerProblem(endKeyword, "${block.openTagText} should be closed with ${block.expectedEndTag}", ReplaceTextFix(block.expectedEnd))
        }
        if (statement is NjkBlockStatement) {
            val endName = (endTag as? NjkEndblockTag)?.endBlockName ?: return
            if (endName.text != statement.name) {
                holder.registerProblem(endName, "{% endblock %} name '${endName.text}' doesn't match block '${statement.name}'", ReplaceTextFix(statement.name.orEmpty()))
            }
        }
    }
}

class NunjucksUnknownKeywordArgumentInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val name = element as? NjkKeywordName ?: return
        val call = PsiTreeUtil.getParentOfType(name, NjkCallExpr::class.java) ?: return
        val macro = resolveCallee(call.expression) ?: return
        if (name.reference?.resolve() == null) {
            holder.registerProblem(name, "Macro '${macro.name}' has no parameter '${name.text}'")
        }
    }
}

/** Object keys passed to GOV.UK-style macros that aren't documented in its macro-options.json. */
class NunjucksMacroOptionsInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val key = element as? NjkDictKey ?: return
        val dict = key.parent?.parent as? NjkDictExpr ?: return
        val options = MacroOptions.optionsAt(dict) ?: return
        val option = options.firstOrNull { it.name == key.keyName }
        when {
            option == null -> holder.registerProblem(key, "Unknown option '${key.keyName}'", ProblemHighlightType.WEAK_WARNING)
            option.deprecated != null -> holder.registerProblem(key, "Option '${key.keyName}' is deprecated since ${option.deprecated}", ProblemHighlightType.LIKE_DEPRECATED)
        }
    }
}

/** nunjucks accepts `[{ a: 1 ]}` – any bracket closes any aggregate – but it's almost always a typo. */
class NunjucksMismatchedBracketInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val expected = when (element) {
            is NjkDictExpr -> "}"
            is NjkArrayExpr -> "]"
            is NjkParenExpr -> ")"
            else -> return
        }
        val closing = element.lastChild ?: return
        if (closing.elementType in CLOSERS && closing.text != expected) {
            holder.registerProblem(closing, "Expected '$expected' but found '${closing.text}'", ReplaceTextFix(expected))
        }
    }

    private companion object {
        val CLOSERS = setOf(NunjucksTypes.RBRACE, NunjucksTypes.RBRACKET, NunjucksTypes.RPAREN)
    }
}

class NunjucksDuplicateBlockInspection : NunjucksInspection() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        val block = element as? NjkBlockStatement ?: return
        val name = block.name ?: return
        val file = NunjucksResolver.nunjucksFile(block) ?: return
        val first = PsiTreeUtil.findChildrenOfType(file, NjkBlockStatement::class.java).first { it.name == name }
        if (first != block) {
            holder.registerProblem(block.nameIdentifier ?: block, "Block '$name' is already defined in this template")
        }
    }
}

enum class KnownNameKind(val label: String) { FILTER("filters"), TEST("tests") }

private class AddKnownNameFix(private val name: String, private val kind: KnownNameKind) : LocalQuickFix {
    override fun getFamilyName() = "Add to known Nunjucks ${kind.label}"

    override fun getName() = "Add '$name' to known Nunjucks ${kind.label}"

    override fun startInWriteAction() = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        NunjucksSettings.getInstance(project).update {
            when (kind) {
                KnownNameKind.FILTER -> extraFilters = (extraFilters + name).distinct().toMutableList()
                KnownNameKind.TEST -> extraTests = (extraTests + name).distinct().toMutableList()
            }
        }
        DaemonCodeAnalyzer.getInstance(project).restart(descriptor.psiElement.containingFile, "Nunjucks known names changed")
    }
}

/** Replaces a single token (keyword, name or bracket) with [replacement]. */
private class ReplaceTextFix(private val replacement: String) : LocalQuickFix {
    override fun getFamilyName() = "Replace with '$replacement'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val document = element.containingFile.viewProvider.document ?: return
        val range = element.textRange
        document.replaceString(range.startOffset, range.endOffset, replacement)
    }
}
