package uk.steers.nunjucks.reference

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import uk.steers.nunjucks.NunjucksLanguage
import uk.steers.nunjucks.file.NunjucksFile
import uk.steers.nunjucks.settings.NunjucksSettings
import java.nio.file.Path

/**
 * Resolves template names the way nunjucks' FileSystemLoader does: relative names (./, ../) against the
 * current template, everything else against the configured search paths.
 *
 * When no search paths are configured we guess the common ones (every `views` directory, plus the
 * govuk-frontend and @govuk-one-login package roots), and finally fall back to matching the path suffix
 * anywhere in the project or its libraries.
 */
object NunjucksTemplateResolver {
    private val AUTO_LIBRARY_ROOTS = listOf(
        "node_modules/govuk-frontend/dist",
        "node_modules/govuk-frontend",
        "node_modules/@govuk-one-login",
    )

    fun isRelative(path: String) = path.startsWith("./") || path.startsWith("../")

    fun resolve(context: PsiFile, path: String): VirtualFile? {
        if (path.isBlank()) return null
        val contextFile = context.originalFile.virtualFile
        if (isRelative(path)) return contextFile?.parent?.findFileByRelativePath(path)?.takeIf { !it.isDirectory }

        val project = context.project
        for (root in searchRoots(project)) {
            root.findFileByRelativePath(path)?.takeIf { !it.isDirectory }?.let { return it }
        }
        return resolveBySuffix(project, path)
    }

    fun resolveNunjucksFile(context: PsiFile, path: String): NunjucksFile? {
        val file = resolve(context, path) ?: return null
        return PsiManager.getInstance(context.project).findViewProvider(file)?.getPsi(NunjucksLanguage) as? NunjucksFile
    }

    /** Search roots from settings, or guessed ones when none are configured. */
    fun searchRoots(project: Project): List<VirtualFile> = CachedValuesManager.getManager(project).getCachedValue(project) {
        CachedValueProvider.Result.create(
            computeSearchRoots(project),
            ProjectRootModificationTracker.getInstance(project),
            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
            NunjucksSettings.getInstance(project).modificationTracker,
        )
    }

    /** The directory that [path] was resolved relative to, for a file found by suffix matching. */
    fun rootOf(file: VirtualFile, path: String): VirtualFile? {
        var dir: VirtualFile? = file.parent
        repeat(path.trim('/').count { it == '/' }) { dir = dir?.parent }
        return dir
    }

    private fun computeSearchRoots(project: Project): List<VirtualFile> {
        val configured = NunjucksSettings.getInstance(project).state.templateRoots
        if (configured.isNotEmpty()) {
            val base = project.guessProjectDir()
            return configured.mapNotNull { root ->
                if (Path.of(root).isAbsolute) LocalFileSystem.getInstance().findFileByPath(root)
                else base?.findFileByRelativePath(root)
            }
        }

        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.toList()
        val viewDirs = FilenameIndex.getVirtualFilesByName("views", GlobalSearchScope.projectScope(project))
            .filter { it.isDirectory && "/node_modules/" !in it.path }
            .sortedBy { it.path.length }
        val libraryRoots = contentRoots.flatMap { content -> AUTO_LIBRARY_ROOTS.mapNotNull { content.findFileByRelativePath(it) } }
        return (viewDirs + libraryRoots + contentRoots).distinct()
    }

    private fun resolveBySuffix(project: Project, path: String): VirtualFile? {
        val normalised = path.trimStart('/')
        val name = normalised.substringAfterLast('/')
        val candidates = FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.allScope(project))
            .filter { !it.isDirectory && (it.path.endsWith("/$normalised") || it.path == normalised) }
        if (candidates.isEmpty()) return null
        val projectScope = GlobalSearchScope.projectScope(project)
        return candidates.firstOrNull { projectScope.contains(it) } ?: candidates.minByOrNull { it.path.length }
    }
}
