package uk.steers.nunjucks.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import uk.steers.nunjucks.psi.*
import uk.steers.nunjucks.psi.impl.identifier

class NunjucksFoldingBuilder : CustomFoldingBuilder(), DumbAware {
    override fun buildLanguageFoldRegions(
        descriptors: MutableList<FoldingDescriptor>,
        root: PsiElement,
        document: Document,
        quick: Boolean,
    ) {
        PsiTreeUtil.processElements(root) { element ->
            when {
                element is NjkStatement -> element.block?.let { foldBlock(it, document, descriptors) }
                element is NjkOutput -> foldIfMultiline(element, document, descriptors)
                element.elementType == NunjucksTypes.COMMENT -> foldIfMultiline(element, document, descriptors)
            }
            true
        }
    }

    private fun foldBlock(block: NunjucksBlock, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        val end = block.endTag ?: return
        val range = TextRange(block.openRange.endOffset, end.textRange.startOffset)
        if (range.isEmpty || !spansLines(range, document)) return
        descriptors.add(FoldingDescriptor(block.statement.node, range, null, "..."))
    }

    private fun foldIfMultiline(element: PsiElement, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        if (spansLines(element.textRange, document)) descriptors.add(FoldingDescriptor(element.node, element.textRange))
    }

    private fun spansLines(range: TextRange, document: Document) =
        document.getLineNumber(range.startOffset) != document.getLineNumber(range.endOffset)

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
        val psi = node.psi
        return when {
            node.elementType == NunjucksTypes.COMMENT -> "{# ... #}"
            psi is NjkOutput -> "{{ ${outputSummary(psi)} }}"
            else -> "..."
        }
    }

    /** `{{ govukButton({ ... }) }}` folds to `{{ govukButton(...) }}`. */
    private fun outputSummary(output: NjkOutput): String {
        val call = output.expression as? NjkCallExpr
        val name = when (val callee = call?.expression) {
            is NjkReferenceExpr -> callee.identifier.text
            is NjkMemberExpr -> callee.text
            else -> null
        }
        return if (name != null) "$name(...)" else "..."
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean = false
}
