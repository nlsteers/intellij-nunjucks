package uk.steers.nunjucks.env

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import uk.steers.nunjucks.settings.NunjucksSettings

/** A filter/global/test registered from JavaScript, e.g. `env.addFilter("formatDate", fn)`. */
data class Registration(val name: String, val fileUrl: String, val offset: Int)

/**
 * Discovers custom filters, tests and globals by scanning JS/TS sources (including node_modules libraries)
 * for nunjucks Environment registration calls, and merges them with names configured in settings.
 */
@Service(Service.Level.PROJECT)
class NunjucksEnvironment(private val project: Project) {
    private data class Registrations(
        val filters: List<Registration>,
        val tests: List<Registration>,
        val globals: List<Registration>,
    )

    fun customFilterNames(): Set<String> =
        registrations().filters.mapTo(sortedSetOf()) { it.name } + NunjucksSettings.getInstance(project).state.extraFilters

    fun customTestNames(): Set<String> =
        registrations().tests.mapTo(sortedSetOf()) { it.name } + NunjucksSettings.getInstance(project).state.extraTests

    fun customGlobalNames(): Set<String> =
        registrations().globals.mapTo(sortedSetOf()) { it.name } + NunjucksSettings.getInstance(project).state.extraGlobals

    fun isKnownFilter(name: String) = name in NunjucksBuiltins.FILTERS || name in customFilterNames()

    fun isKnownTest(name: String) = name in NunjucksBuiltins.TESTS || name in customTestNames()

    fun filterRegistrations(name: String): List<PsiElement> = toPsi(registrations().filters.filter { it.name == name })

    fun globalRegistrations(name: String): List<PsiElement> = toPsi(registrations().globals.filter { it.name == name })

    fun registrationFile(name: String): String? =
        registrations().let { it.filters + it.tests + it.globals }.firstOrNull { it.name == name }?.fileUrl?.substringAfterLast('/')

    private fun toPsi(registrations: List<Registration>): List<PsiElement> {
        val manager = PsiManager.getInstance(project)
        val vfm = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
        return registrations.mapNotNull { registration ->
            val file = vfm.findFileByUrl(registration.fileUrl) ?: return@mapNotNull null
            manager.findFile(file)?.findElementAt(registration.offset)
        }
    }

    private fun registrations(): Registrations = CachedValuesManager.getManager(project).getCachedValue(project) {
        CachedValueProvider.Result.create(
            scan(),
            PsiModificationTracker.getInstance(project).forLanguages { it.id != "Nunjucks" && it.id != "HTML" },
            ProjectRootModificationTracker.getInstance(project),
        )
    }

    private fun scan(): Registrations {
        val filters = mutableListOf<Registration>()
        val tests = mutableListOf<Registration>()
        val globals = mutableListOf<Registration>()
        val helper = PsiSearchHelper.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)
        for ((word, into) in listOf("addFilter" to filters, "addTest" to tests, "addGlobal" to globals)) {
            helper.processAllFilesWithWord(word, scope, { file ->
                val virtualFile = file.virtualFile
                if (virtualFile != null && virtualFile.extension in SCRIPT_EXTENSIONS) {
                    REGISTRATION.findAll(file.viewProvider.contents).forEach { match ->
                        if (match.groupValues[1] == word) {
                            into.add(Registration(match.groupValues[2], virtualFile.url, match.groups[2]!!.range.first))
                        }
                    }
                }
                true
            }, true)
        }
        return Registrations(filters, tests, globals)
    }

    companion object {
        private val SCRIPT_EXTENSIONS = setOf("js", "mjs", "cjs", "ts", "mts", "cts")
        private val REGISTRATION = Regex("""\b(addFilter|addTest|addGlobal)\s*\(\s*['"`]([\w$]+)['"`]""")

        fun getInstance(project: Project): NunjucksEnvironment = project.service()
    }
}
