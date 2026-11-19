package uk.steers.nunjucks

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

abstract class NunjucksTestCase : BasePlatformTestCase() {
    override fun getTestDataPath(): String = File("src/test/testData").absolutePath

    protected fun nunjucksPsi(file: PsiFile): PsiFile = file.viewProvider.getPsi(NunjucksLanguage)!!

    protected fun errors(file: PsiFile): List<String> =
        PsiTreeUtil.findChildrenOfType(nunjucksPsi(file), PsiErrorElement::class.java).map { error ->
            val line = file.viewProvider.document!!.getLineNumber(error.textOffset) + 1
            "line $line: ${error.errorDescription} at '${error.parent.text.take(60)}'"
        }
}
