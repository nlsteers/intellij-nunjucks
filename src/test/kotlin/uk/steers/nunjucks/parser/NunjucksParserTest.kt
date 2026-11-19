package uk.steers.nunjucks.parser

import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.UsefulTestCase
import uk.steers.nunjucks.NunjucksTestCase
import java.io.File

/**
 * Each testData/parser/<name>.njk is parsed and its PSI tree compared with <name>.txt.
 * A missing .txt is created from the current output and the test fails, so new trees get reviewed.
 */
class NunjucksParserTest : NunjucksTestCase() {
    fun testTags() = doTest()
    fun testExpressions() = doTest()
    fun testInheritance() = doTest()
    fun testMacros() = doTest()
    fun testRaw() = doTest()
    fun testWhitespaceControl() = doTest()
    fun testRecovery() = doTest()

    private fun doTest() {
        val name = getTestName(true)
        val source = File(testDataPath, "parser/$name.njk")
        val file = myFixture.configureByText("$name.njk", source.readText())
        val actual = DebugUtil.psiToString(nunjucksPsi(file), true, false)
        val expected = File(testDataPath, "parser/$name.txt")
        if (!expected.exists()) {
            expected.writeText(actual)
            fail("Created ${expected.path}; review it and re-run")
        }
        UsefulTestCase.assertSameLines(expected.readText(), actual)
    }
}
