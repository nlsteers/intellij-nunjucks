package uk.steers.nunjucks.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings

/** The tags that make up a block statement such as `{% if %}…{% elif %}…{% endif %}`. */
class NunjucksBlock(
    val statement: NjkStatement,
    /** Range of the opening tag, from `{%` to `%}`. */
    val openRange: TextRange,
    val openKeyword: PsiElement,
    val middleTags: List<PsiElement>,
    val endTag: PsiElement?,
    /** The end keyword this block should be closed with, e.g. `endfor` for asyncEach is `endeach`. */
    val expectedEnd: String,
) {
    val openTagText: String get() = "{% ${openKeyword.text} %}"
    val expectedEndTag: String get() = "{% $expectedEnd %}"
    val isClosed: Boolean get() = endTag != null

    /** Tag keyword tokens of every tag in this block, for highlighting/navigation. */
    fun keywordTokens(): List<PsiElement> =
        listOf(openKeyword) + (middleTags + listOfNotNull(endTag)).mapNotNull { tagKeyword(it) }

    companion object {
        fun of(element: PsiElement?): NunjucksBlock? = when (element) {
            is NjkIfStatement -> NunjucksBlock(
                element, element.ifTag.textRange, tagKeyword(element.ifTag)!!,
                element.elifBranchList.map { it.elifTag } + listOfNotNull(element.elseBranch?.elseTag),
                element.endifTag, "endif",
            )
            is NjkForStatement -> {
                val keyword = tagKeyword(element.forTag)!!
                val end = when (keyword.elementType) {
                    NunjucksTypes.ASYNCEACH -> "endeach"
                    NunjucksTypes.ASYNCALL -> "endall"
                    else -> "endfor"
                }
                NunjucksBlock(element, element.forTag.textRange, keyword, listOfNotNull(element.elseBranch?.elseTag), element.endforTag, end)
            }
            is NjkSetStatement -> element.body?.let {
                val keyword = element.node.findChildByType(NunjucksTypes.SET)!!.psi
                val openEnd = element.node.findChildByType(NunjucksTypes.BLOCK_END)?.textRange?.endOffset ?: it.textRange.startOffset
                NunjucksBlock(element, TextRange(element.textRange.startOffset, openEnd), keyword, emptyList(), element.endsetTag, "endset")
            }
            is NjkMacroStatement -> NunjucksBlock(element, element.macroTag.textRange, tagKeyword(element.macroTag)!!, emptyList(), element.endmacroTag, "endmacro")
            is NjkCallStatement -> NunjucksBlock(element, element.callTag.textRange, tagKeyword(element.callTag)!!, emptyList(), element.endcallTag, "endcall")
            is NjkFilterStatement -> NunjucksBlock(element, element.filterTag.textRange, tagKeyword(element.filterTag)!!, emptyList(), element.endfilterTag, "endfilter")
            is NjkBlockStatement -> NunjucksBlock(element, element.blockTag.textRange, tagKeyword(element.blockTag)!!, emptyList(), element.endblockTag, "endblock")
            is NjkRawStatement -> {
                val keyword = tagKeyword(element.rawTag)!!
                val end = if (keyword.elementType == NunjucksTypes.VERBATIM) "endverbatim" else "endraw"
                NunjucksBlock(element, element.rawTag.textRange, keyword, emptyList(), element.endrawTag, end)
            }
            else -> null
        }

        /** The innermost block whose tag (open, middle or end) contains [element]. */
        fun containingTag(element: PsiElement): NunjucksBlock? {
            var current: PsiElement? = element
            while (current != null && current !is com.intellij.psi.PsiFile) {
                if (current is NjkBody) return null
                of(current)?.let { block ->
                    val offset = element.textRange.startOffset
                    val inTag = block.openRange.contains(offset) ||
                        (block.middleTags + listOfNotNull(block.endTag)).any { it.textRange.contains(offset) }
                    if (inTag) return block
                }
                current = current.parent
            }
            return null
        }

        /** Blocks enclosing [element], innermost first. */
        fun enclosing(element: PsiElement): List<NunjucksBlock> =
            generateSequence(element.parent) { if (it is com.intellij.psi.PsiFile) null else it.parent }
                .mapNotNull { of(it) }
                .toList()

        /** The keyword after `{%` in a tag. */
        fun tagKeyword(tag: PsiElement): PsiElement? =
            tag.firstChild?.siblings()?.firstOrNull { it.elementType != NunjucksTypes.BLOCK_START && it !is com.intellij.psi.PsiWhiteSpace }
                ?.takeIf { it.node.firstChildNode == null }
    }
}

val NjkStatement.block: NunjucksBlock? get() = NunjucksBlock.of(this)

fun PsiElement.parentStatement(): NjkStatement? = PsiTreeUtil.getParentOfType(this, NjkStatement::class.java)
