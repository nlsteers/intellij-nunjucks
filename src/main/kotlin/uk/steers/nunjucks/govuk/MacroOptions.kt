package uk.steers.nunjucks.govuk

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonBooleanLiteral
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.psi.impl.keyName
import uk.steers.nunjucks.reference.resolveCallee

/** One entry of a GOV.UK-style `macro-options.json`. */
data class MacroOption(
    val name: String,
    val type: String,
    val required: Boolean,
    val description: String,
    val params: List<MacroOption>,
    val isComponent: Boolean,
    val deprecated: String?,
    /** The json element defining this option, for navigation. */
    val definition: PsiElement,
)

/**
 * GOV.UK Frontend (and compatible design systems) document each component macro's `params` object in a
 * `macro-options.json` next to its `macro.njk`. This resolves the options that apply to an object literal
 * passed to such a macro, following nested `params`, arrays of objects and `isComponent` options.
 */
object MacroOptions {
    private const val OPTIONS_FILE = "macro-options.json"

    /** Options for the object literal [dict], or null if it isn't (part of) a documented macro's params. */
    fun optionsAt(dict: NjkDictExpr): List<MacroOption>? = when (val parent = dict.parent) {
        is NjkArgumentList -> {
            val call = parent.parent as? NjkCallExpr
            if (call == null || parent.firstArgument() != dict) null
            else resolveCallee(call.expression)?.let { optionsFor(it) }
        }
        is NjkDictEntry -> optionOf(parent)?.let { childOptions(it) }
        is NjkArrayExpr -> (parent.parent as? NjkDictEntry)?.let { optionOf(it) }?.let { childOptions(it) }
        else -> null
    }

    fun optionFor(key: NjkDictKey): MacroOption? = (key.parent as? NjkDictEntry)?.let { optionOf(it) }

    fun addKeyCompletions(key: NjkDictKey, result: CompletionResultSet) {
        val entry = key.parent as? NjkDictEntry ?: return
        val dict = entry.parent as? NjkDictExpr ?: return
        val options = optionsAt(dict) ?: return
        val present = dict.dictEntryList.filter { it != entry }.mapTo(mutableSetOf()) { it.dictKey.keyName }
        options.filter { it.name !in present }.forEach { option ->
            val lookup = LookupElementBuilder.create(option.definition, option.name)
                .withIcon(AllIcons.Nodes.Property)
                .withTypeText(option.type + if (option.required) " (required)" else "")
                .withTailText(option.deprecated?.let { " deprecated in $it" }, true)
                .withStrikeoutness(option.deprecated != null)
                .withInsertHandler(ColonInsertHandler)
            result.addElement(PrioritizedLookupElement.withPriority(lookup, if (option.required) 10.0 else 0.0))
        }
        result.stopHere()
    }

    private fun optionOf(entry: NjkDictEntry): MacroOption? {
        val dict = entry.parent as? NjkDictExpr ?: return null
        val name = entry.dictKey.keyName
        return optionsAt(dict)?.firstOrNull { it.name == name }
    }

    private fun NjkArgumentList.firstArgument(): PsiElement? = children.firstOrNull { it is NjkExpression || it is NjkKeywordArgument }

    fun optionsFor(macro: NjkMacroStatement): List<MacroOption>? {
        val directory = macro.containingFile.originalFile.virtualFile?.parent ?: return null
        return load(macro.project, directory)
    }

    private fun childOptions(option: MacroOption): List<MacroOption>? {
        if (option.params.isNotEmpty()) return option.params
        if (!option.isComponent) return null
        // e.g. `errorMessage` in govuk/components/input is documented by govuk/components/error-message
        val componentsDirectory = option.definition.containingFile.virtualFile?.parent?.parent ?: return null
        val component = componentsDirectory.findChild(kebabCase(option.name)) ?: return null
        return load(option.definition.project, component)
    }

    private fun load(project: com.intellij.openapi.project.Project, directory: VirtualFile): List<MacroOption>? {
        val file = directory.findChild(OPTIONS_FILE) ?: return null
        val json = PsiManager.getInstance(project).findFile(file) as? JsonFile ?: return null
        return CachedValuesManager.getCachedValue(json) {
            val options = (json.topLevelValue as? JsonArray)?.let { parseOptions(it) }.orEmpty()
            CachedValueProvider.Result.create(options, json)
        }
    }

    private fun parseOptions(array: JsonArray): List<MacroOption> = array.valueList.filterIsInstance<JsonObject>().mapNotNull { obj ->
        val name = (obj.findProperty("name")?.value as? JsonStringLiteral)?.value ?: return@mapNotNull null
        MacroOption(
            name = name,
            type = (obj.findProperty("type")?.value as? JsonStringLiteral)?.value ?: "any",
            required = (obj.findProperty("required")?.value as? JsonBooleanLiteral)?.value ?: false,
            description = (obj.findProperty("description")?.value as? JsonStringLiteral)?.value.orEmpty(),
            params = (obj.findProperty("params")?.value as? JsonArray)?.let { parseOptions(it) }.orEmpty(),
            isComponent = (obj.findProperty("isComponent")?.value as? JsonBooleanLiteral)?.value ?: false,
            deprecated = (obj.findProperty("deprecated")?.value as? JsonStringLiteral)?.value,
            definition = obj.findProperty("name") ?: obj,
        )
    }

    private fun kebabCase(name: String) = name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

    private object ColonInsertHandler : InsertHandler<LookupElement> {
        override fun handleInsert(context: com.intellij.codeInsight.completion.InsertionContext, item: LookupElement) {
            val text = context.document.charsSequence
            if (text.getOrNull(context.tailOffset) == ':') return
            context.document.insertString(context.tailOffset, ": ")
            context.editor.caretModel.moveToOffset(context.tailOffset + 2)
        }
    }
}
