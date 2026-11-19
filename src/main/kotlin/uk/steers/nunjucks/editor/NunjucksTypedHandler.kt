package uk.steers.nunjucks.editor

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.file.NunjucksFileViewProvider
import uk.steers.nunjucks.psi.NunjucksBlock

/**
 * - `{{`, `{%` and `{#` insert their closing delimiter: `{{ | }}`.
 * - Typing the `%}` of an opening block tag inserts the matching end tag if the block isn't closed yet.
 */
class NunjucksTypedHandler : TypedHandlerDelegate() {
    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file.viewProvider !is NunjucksFileViewProvider) return Result.CONTINUE
        val closing = CLOSING_DELIMITERS[c] ?: return Result.CONTINUE
        val text = editor.document.charsSequence
        val offset = editor.caretModel.offset
        if (offset < 1 || text[offset - 1] != '{' || (offset >= 2 && text[offset - 2] == '{')) return Result.CONTINUE

        val following = text.subSequence(offset, minOf(offset + 4, text.length)).trimStart()
        if (following.startsWith(closing)) return Result.CONTINUE

        editor.document.insertString(offset, "$c  $closing")
        editor.caretModel.moveToOffset(offset + 2)
        return Result.STOP
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '}' || file.viewProvider !is NunjucksFileViewProvider) return Result.CONTINUE
        val offset = editor.caretModel.offset
        if (offset < 2 || editor.document.charsSequence[offset - 2] != '%') return Result.CONTINUE

        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val leaf = file.viewProvider.getPsi(NunjucksLanguage)?.findElementAt(offset - 1) ?: return Result.CONTINUE
        val block = NunjucksBlock.containingTag(leaf) ?: return Result.CONTINUE
        if (block.openRange.endOffset == offset && !block.isClosed) {
            editor.document.insertString(offset, block.expectedEndTag)
        }
        return Result.CONTINUE
    }

    private companion object {
        val CLOSING_DELIMITERS = mapOf('{' to "}}", '%' to "%}", '#' to "#}")
    }
}
