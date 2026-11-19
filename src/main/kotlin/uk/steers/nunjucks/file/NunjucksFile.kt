package uk.steers.nunjucks.file

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import uk.steers.nunjucks.NunjucksLanguage

class NunjucksFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NunjucksLanguage) {
    override fun getFileType(): FileType = NunjucksFileType

    override fun toString(): String = "Nunjucks file"
}
