package uk.steers.nunjucks.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.NunjucksIcons
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.env.NunjucksBuiltins
import uk.steers.nunjucks.env.NunjucksEnvironment
import uk.steers.nunjucks.govuk.MacroOptions
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.reference.DeclarationKind
import uk.steers.nunjucks.reference.NunjucksMemberReference
import uk.steers.nunjucks.reference.NunjucksResolver
import uk.steers.nunjucks.reference.resolveCallee

class NunjucksCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        if (position.language != NunjucksLanguage) return
        val parent = position.parent

        when {
            isTagNamePosition(position) -> addTagNames(position, result)
            parent is NjkFilterName -> addFilters(position, result)
            parent is NjkReferenceExpr && isTestPosition(parent) -> addTests(position, result)
            parent is NjkMemberExpr -> addMembers(parent, result)
            parent is NjkDictKey -> MacroOptions.addKeyCompletions(parent, result)
            parent is NjkReferenceExpr -> addExpressionNames(parent, result)
            parent is NjkKeywordName -> addKeywordArguments(parent, result)
        }
    }

    /** `x is <test>` or `x is <test>(args)`. */
    private fun isTestPosition(reference: NjkReferenceExpr): Boolean {
        val test = (reference.parent as? NjkCallExpr)?.takeIf { it.expression == reference } ?: reference
        val isExpr = test.parent as? NjkIsExpr ?: return false
        return isExpr.expressionList.getOrNull(1) == test
    }

    private fun isTagNamePosition(position: PsiElement): Boolean {
        val type = position.elementType
        if (type != NunjucksTypes.TAG_NAME && type !in NunjucksTokenSets.TAG_KEYWORDS) return false
        return PsiTreeUtil.skipWhitespacesBackward(position).elementType == NunjucksTypes.BLOCK_START
    }

    private fun addTagNames(position: PsiElement, result: CompletionResultSet) {
        val enclosing = NunjucksBlock.enclosing(position)
        enclosing.firstOrNull { !it.isClosed }?.let { result.addElement(priority(keyword(it.expectedEnd, "close ${it.openTagText}"), 100.0)) }
        enclosing.filter { !it.isClosed }.drop(1).forEach { result.addElement(priority(keyword(it.expectedEnd, "close ${it.openTagText}"), 50.0)) }

        val innermost = enclosing.firstOrNull()?.statement
        if (innermost is NjkIfStatement) listOf("elif", "else").forEach { result.addElement(priority(keyword(it, null), 40.0)) }
        if (innermost is NjkForStatement) result.addElement(priority(keyword("else", null), 40.0))

        BLOCK_TAGS.forEach { (name, end) -> result.addElement(keyword(name, null).withInsertHandler(EndTagInsertHandler(end))) }
        SIMPLE_TAGS.forEach { result.addElement(keyword(it, null).withInsertHandler(SpaceInsertHandler)) }
        result.stopHere()
    }

    private fun addFilters(position: PsiElement, result: CompletionResultSet) {
        NunjucksBuiltins.FILTERS.values.forEach { filter ->
            result.addElement(
                LookupElementBuilder.create(filter.name).withIcon(AllIcons.Nodes.Function)
                    .withTailText(filter.signature, true).withTypeText(filter.aliasOf?.let { "alias of $it" } ?: "builtin")
                    .withInsertHandler(if (filter.requiresArguments) ParenthesesInsertHandler else null),
            )
        }
        val environment = NunjucksEnvironment.getInstance(position.project)
        environment.customFilterNames().forEach { name ->
            result.addElement(
                LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Function).withTypeText(environment.registrationFile(name) ?: "custom"),
            )
        }
        result.stopHere()
    }

    private fun addTests(position: PsiElement, result: CompletionResultSet) {
        NunjucksBuiltins.TESTS.values.forEach { test ->
            result.addElement(LookupElementBuilder.create(test.name).withIcon(AllIcons.Nodes.Test).withTailText(test.signature, true).withTypeText("test"))
        }
        NunjucksEnvironment.getInstance(position.project).customTestNames().forEach {
            result.addElement(LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Test).withTypeText("custom test"))
        }
    }

    private fun addMembers(member: NjkMemberExpr, result: CompletionResultSet) {
        val qualifier = member.expression
        if (qualifier is NjkReferenceExpr && qualifier.text == "loop" && qualifier.reference?.resolve() == null &&
            PsiTreeUtil.getParentOfType(member, NjkForStatement::class.java) != null
        ) {
            NunjucksBuiltins.LOOP_PROPERTIES.forEach { (name, description) ->
                result.addElement(LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Property).withTypeText(description, true))
            }
            return
        }
        (member.reference as? NunjucksMemberReference)?.importedFile()?.let { file ->
            NunjucksResolver.macros(file).forEach { macro -> macro.name?.let { result.addElement(macroLookup(macro, it)) } }
        }
    }

    private fun addExpressionNames(reference: NjkReferenceExpr, result: CompletionResultSet) {
        val seen = mutableSetOf<String>()
        fun add(element: LookupElement) {
            if (seen.add(element.lookupString)) result.addElement(element)
        }

        PsiTreeUtil.getParentOfType(reference, NjkArgumentList::class.java)?.let { arguments ->
            if (arguments.parent is NjkCallExpr && reference.parent == arguments) {
                resolveCallee((arguments.parent as NjkCallExpr).expression)?.let { macro ->
                    macroParameters(macro).forEach { name ->
                        add(priority(LookupElementBuilder.create("$name=").withIcon(AllIcons.Nodes.Parameter).withTypeText("keyword argument"), 90.0))
                    }
                }
            }
        }

        NunjucksResolver.visibleDeclarations(reference).forEach { declaration ->
            if (declaration.name.isEmpty()) return@forEach
            val element = declaration.element
            val lookup = when (declaration.kind) {
                DeclarationKind.MACRO -> macroLookup(element as NjkMacroStatement, declaration.name)
                DeclarationKind.IMPORTED_MACRO -> {
                    val macro = NunjucksResolver.resolveImportedMacro(element as NjkImportSpec)
                    if (macro != null) macroLookup(macro, declaration.name) else LookupElementBuilder.create(declaration.name).withIcon(AllIcons.Nodes.Function)
                }
                DeclarationKind.IMPORT_ALIAS -> LookupElementBuilder.create(declaration.name).withIcon(AllIcons.Nodes.Package).withTypeText("import")
                DeclarationKind.PARAMETER -> LookupElementBuilder.create(declaration.name).withIcon(AllIcons.Nodes.Parameter)
                DeclarationKind.LOOP_VARIABLE, DeclarationKind.VARIABLE -> LookupElementBuilder.create(declaration.name).withIcon(AllIcons.Nodes.Variable)
            }
            add(priority(lookup, 20.0))
        }

        if (PsiTreeUtil.getParentOfType(reference, NjkForStatement::class.java) != null) {
            add(LookupElementBuilder.create("loop").withIcon(AllIcons.Nodes.Variable).withTypeText("loop variables"))
        }
        if (PsiTreeUtil.getParentOfType(reference, NjkMacroStatement::class.java) != null) {
            add(LookupElementBuilder.create("caller").withIcon(AllIcons.Nodes.Function).withTailText("()", true).withInsertHandler(ParenthesesInsertHandler))
        }
        NunjucksBuiltins.GLOBALS.values.forEach { global ->
            add(LookupElementBuilder.create(global.name).withIcon(AllIcons.Nodes.Function).withTailText(global.signature, true).withTypeText("global").withInsertHandler(ParenthesesInsertHandler))
        }
        val environment = NunjucksEnvironment.getInstance(reference.project)
        environment.customGlobalNames().forEach { name ->
            add(LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Variable).withTypeText(environment.registrationFile(name) ?: "global"))
        }
        EXPRESSION_KEYWORDS.forEach { add(priority(LookupElementBuilder.create(it).bold(), -10.0)) }
    }

    private fun addKeywordArguments(name: NjkKeywordName, result: CompletionResultSet) {
        val call = PsiTreeUtil.getParentOfType(name, NjkCallExpr::class.java) ?: return
        val macro = resolveCallee(call.expression) ?: return
        macroParameters(macro).forEach { result.addElement(LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Parameter)) }
    }

    private fun macroParameters(macro: NjkMacroStatement): List<String> =
        macro.macroTag.parameterList?.parameterList.orEmpty().mapNotNull { it.name }

    private fun macroLookup(macro: NjkMacroStatement, name: String): LookupElement =
        LookupElementBuilder.create(macro, name)
            .withIcon(AllIcons.Nodes.Function)
            .withTailText(macro.macroTag.parameterList?.text ?: "()", true)
            .withTypeText(macro.containingFile.name)
            .withInsertHandler(ParenthesesInsertHandler)

    private fun keyword(name: String, type: String?): LookupElementBuilder =
        LookupElementBuilder.create(name).bold().withIcon(NunjucksIcons.FILE).withTypeText(type)

    private fun priority(element: LookupElement, priority: Double): LookupElement = PrioritizedLookupElement.withPriority(element, priority)

    private companion object {
        val BLOCK_TAGS = linkedMapOf(
            "if" to "endif", "for" to "endfor", "macro" to "endmacro", "call" to "endcall", "filter" to "endfilter",
            "block" to "endblock", "raw" to "endraw", "verbatim" to "endverbatim",
            "asyncEach" to "endeach", "asyncAll" to "endall", "ifAsync" to "endif",
        )
        val SIMPLE_TAGS = listOf("set", "extends", "include", "import", "from")
        val EXPRESSION_KEYWORDS = listOf("true", "false", "none", "not")
    }
}

private object SpaceInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: com.intellij.codeInsight.completion.InsertionContext, item: LookupElement) {
        val document = context.document
        if (document.charsSequence.getOrNull(context.tailOffset) != ' ') document.insertString(context.tailOffset, " ")
        context.editor.caretModel.moveToOffset(context.tailOffset + 1)
    }
}

private object ParenthesesInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: com.intellij.codeInsight.completion.InsertionContext, item: LookupElement) {
        val document = context.document
        if (document.charsSequence.getOrNull(context.tailOffset) == '(') return
        document.insertString(context.tailOffset, "()")
        context.editor.caretModel.moveToOffset(context.tailOffset - 1)
    }
}

/** Completing `{% if| %}` also adds `{% endif %}` after the tag when the new block isn't closed. */
private class EndTagInsertHandler(private val endKeyword: String) : InsertHandler<LookupElement> {
    override fun handleInsert(context: com.intellij.codeInsight.completion.InsertionContext, item: LookupElement) {
        SpaceInsertHandler.handleInsert(context, item)
        val document = context.document
        val caret = context.editor.caretModel.offset
        PsiDocumentManager.getInstance(context.project).commitDocument(document)
        val psi = context.file.viewProvider.getPsi(NunjucksLanguage) ?: return
        val leaf = psi.findElementAt(context.startOffset) ?: return
        val block = NunjucksBlock.containingTag(leaf) ?: return
        if (block.isClosed || block.expectedEnd != endKeyword) return
        val tagEnd = block.openRange.endOffset
        val text = document.charsSequence
        if (tagEnd < 2 || text.subSequence(tagEnd - 2, tagEnd) != "%}") return
        document.insertString(tagEnd, "{% $endKeyword %}")
        context.editor.caretModel.moveToOffset(caret)
    }
}
