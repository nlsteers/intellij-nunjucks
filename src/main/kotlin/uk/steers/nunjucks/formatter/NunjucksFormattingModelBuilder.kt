// Derived from HbFormattingModelBuilder in the JetBrains Handlebars plugin (https://github.com/JetBrains/intellij-plugins),
// Copyright 2000-2025 JetBrains s.r.o. and contributors, Apache License 2.0. Modified for Nunjucks; see NOTICE.

package uk.steers.nunjucks.formatter

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.Spacing
import com.intellij.lang.html.HTMLLanguage
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.formatting.templateLanguages.TemplateLanguageBlock
import com.intellij.formatting.templateLanguages.TemplateLanguageBlockFactory
import com.intellij.formatting.templateLanguages.TemplateLanguageFormattingModelBuilder
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.formatter.FormattingDocumentModelImpl
import com.intellij.psi.formatter.xml.HtmlPolicy
import com.intellij.psi.formatter.xml.SyntheticBlock
import com.intellij.psi.templateLanguages.SimpleTemplateLanguageFormattingModelBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlTag
import uk.steers.nunjucks.file.NunjucksTemplateData
import uk.steers.nunjucks.psi.*

/**
 * Indents Nunjucks block bodies while delegating template text to the data language's formatter,
 * following the approach of the bundled Handlebars plugin. Spacing inside tags is left as written.
 */
class NunjucksFormattingModelBuilder : TemplateLanguageFormattingModelBuilder() {
    override fun createTemplateLanguageBlock(
        node: ASTNode,
        wrap: Wrap?,
        alignment: Alignment?,
        foreignChildren: MutableList<DataLanguageBlockWrapper>?,
        codeStyleSettings: CodeStyleSettings,
    ): TemplateLanguageBlock {
        val documentModel = FormattingDocumentModelImpl.createOn(node.psi.containingFile)
        return NunjucksFormattingBlock(node, wrap, alignment, this, codeStyleSettings, foreignChildren, HtmlPolicy(codeStyleSettings, documentModel))
    }

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.containingFile
        // the data language delegates our outer fragments back to us; give it a dummy model to avoid recursion
        if (formattingContext.node.elementType == NunjucksTemplateData.OUTER_ELEMENT_TYPE) {
            return SimpleTemplateLanguageFormattingModelBuilder().createModel(formattingContext)
        }
        val rootBlock = getRootBlock(file, file.viewProvider, formattingContext.codeStyleSettings)
        return DocumentBasedFormattingModel(rootBlock, formattingContext.project, formattingContext.codeStyleSettings, file.fileType, file)
    }

    override fun dontFormatMyModel(): Boolean = false
}

private class NunjucksFormattingBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    blockFactory: TemplateLanguageBlockFactory,
    settings: CodeStyleSettings,
    foreignChildren: List<DataLanguageBlockWrapper>?,
    private val htmlPolicy: HtmlPolicy,
) : TemplateLanguageBlock(node, wrap, alignment, blockFactory, settings, foreignChildren) {

    /**
     * Block bodies are indented from the "outside" when not inside a data language (HTML) element, and from the
     * "inside" (each child indents itself) when they are, so the two formatters' indents interleave without
     * double-indenting. See HbFormattingModelBuilder in the Handlebars plugin for the full rationale.
     */
    override fun getIndent(): Indent? {
        if (myNode.text.isBlank()) return Indent.getNoneIndent()
        if (insideWhitespacePreservingTag()) return null
        val psi = myNode.psi

        if (psi is NjkBody) {
            val foreignParent = foreignBlockParent(immediate = false) ?: return Indent.getNormalIndent()
            val tag = foreignParent.node as? XmlTag
            return if (tag != null && !htmlPolicy.indentChildrenOf(tag)) Indent.getNormalIndent() else Indent.getNoneIndent()
        }

        if (myNode.treeParent?.psi is NjkBody) {
            val parent = parent
            if (parent is NunjucksFormattingBlock && parent.indent == Indent.getNoneIndent()) return Indent.getNormalIndent()
        }

        if (isAggregateContent(myNode)) return Indent.getNormalIndent()
        if (psi is NjkExpression && psi.parent is NjkOutput) return Indent.getNormalIndent()

        val foreignParent = foreignBlockParent(immediate = true)
        if (foreignParent != null) {
            val tag = foreignParent.node as? XmlTag
            return if (tag != null && !htmlPolicy.indentChildrenOf(tag)) Indent.getNoneIndent() else Indent.getNormalIndent()
        }
        return Indent.getNoneIndent()
    }

    /** Never add or remove line breaks where Nunjucks meets the data language: `{% if x %}<b>y</b>{% endif %}` stays as written. */
    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        if (child1 is DataLanguageBlockWrapper || child2 is DataLanguageBlockWrapper) keepSpacing() else super.getSpacing(child1, child2)

    override fun getLeftNeighborSpacing(block: Block?, child: DataLanguageBlockWrapper, index: Int): Spacing = keepSpacing()

    override fun getRightNeighborSpacing(block: Block, child: DataLanguageBlockWrapper, index: Int): Spacing = keepSpacing()

    private fun keepSpacing(): Spacing =
        if (insideWhitespacePreservingTag()) Spacing.getReadOnlySpacing()
        else Spacing.createSpacing(0, Int.MAX_VALUE, 0, true, settings.getCommonSettings(HTMLLanguage.INSTANCE).KEEP_BLANK_LINES_IN_CODE)

    private fun insideWhitespacePreservingTag(): Boolean {
        var current = parent
        while (current != null) {
            val tag = (current as? DataLanguageBlockWrapper)?.node as? XmlTag
            if (tag != null && htmlPolicy.keepWhiteSpacesInsideTag(tag)) return true
            current = current.parent
        }
        return false
    }

    override fun getTemplateTextElementType(): IElementType = NunjucksTypes.TEMPLATE_TEXT

    override fun isRequiredRange(range: TextRange): Boolean = false

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val psi = myNode.psi
        if (isBlockWrapper(psi) ||
            (parent is DataLanguageBlockWrapper && (psi !is NjkBody || newChildIndex != 0 || myNode.treeNext is PsiErrorElement))
        ) {
            return ChildAttributes(Indent.getNormalIndent(), null)
        }
        if (isAggregate(psi)) return ChildAttributes(Indent.getNormalIndent(), null)
        return ChildAttributes(Indent.getNoneIndent(), null)
    }

    private fun foreignBlockParent(immediate: Boolean): DataLanguageBlockWrapper? {
        var current = parent
        while (current != null) {
            if (current is DataLanguageBlockWrapper && current.original !is SyntheticBlock) return current
            if (immediate && current is NunjucksFormattingBlock) return null
            current = current.parent
        }
        return null
    }

    private companion object {
        fun isBlockWrapper(psi: com.intellij.psi.PsiElement) =
            (psi is NjkStatement && psi.block != null) || psi is NjkElifBranch || psi is NjkElseBranch

        fun isAggregate(psi: com.intellij.psi.PsiElement) =
            psi is NjkDictExpr || psi is NjkArrayExpr || psi is NjkArgumentList || psi is NjkParameterList || psi is NjkParenExpr

        /** Entries of `{ }`, `[ ]` and `( )` – but not their brackets – indent relative to the aggregate. */
        fun isAggregateContent(node: ASTNode): Boolean {
            val parent = node.treeParent?.psi ?: return false
            if (!isAggregate(parent)) return false
            return node.elementType !in BRACKETS
        }

        val BRACKETS = setOf(
            NunjucksTypes.LBRACE, NunjucksTypes.RBRACE, NunjucksTypes.LBRACKET, NunjucksTypes.RBRACKET,
            NunjucksTypes.LPAREN, NunjucksTypes.RPAREN,
        )
    }
}
