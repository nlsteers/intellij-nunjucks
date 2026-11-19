package uk.steers.nunjucks.parser

import uk.steers.nunjucks.NunjucksTestCase
import java.io.File

/**
 * Parses every template under the directories listed in the `nunjucks.corpus` system property
 * (path-separator separated) and asserts none produce parse errors. Skipped when unset.
 */
class NunjucksCorpusTest : NunjucksTestCase() {
    fun testCorpusParsesWithoutErrors() {
        val roots = System.getProperty("nunjucks.corpus").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        if (roots.isEmpty()) return

        val failures = mutableListOf<String>()
        var count = 0
        roots.map(::File).filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "njk" }
                .forEach { template ->
                    count++
                    val file = myFixture.configureByText("corpus$count.njk", template.readText())
                    errors(file).forEach { failures.add("${template.path} $it") }
                }
        }
        println("Parsed $count corpus templates")
        assertTrue("Parse errors:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
