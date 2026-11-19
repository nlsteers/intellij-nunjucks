package uk.steers.nunjucks.file

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import uk.steers.nunjucks.settings.NunjucksAppSettings
import java.io.IOException

/**
 * Opens `.html` files as Nunjucks templates when they contain Nunjucks tags, as Express apps commonly use
 * `view engine html`. Only `{% tag %}` statements count: `{{ }}` alone is too common in other template syntaxes.
 */
class NunjucksHtmlFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file.isDirectory || file.extension?.lowercase() !in HTML_EXTENSIONS) return null
        if (!NunjucksAppSettings.getInstance().state.detectInHtmlFiles) return null
        return if (containsNunjucks(file)) NunjucksFileType else null
    }

    private fun containsNunjucks(file: VirtualFile): Boolean {
        val stamp = file.modificationStamp
        file.getUserData(DETECTED)?.let { (cachedStamp, detected) -> if (cachedStamp == stamp) return detected }
        val detected = try {
            val bytes = file.inputStream.use { it.readNBytes(SCAN_LIMIT) }
            NUNJUCKS_TAG.containsMatchIn(String(bytes, Charsets.UTF_8))
        } catch (_: IOException) {
            false
        }
        file.putUserData(DETECTED, stamp to detected)
        return detected
    }

    private companion object {
        val HTML_EXTENSIONS = setOf("html", "htm")
        const val SCAN_LIMIT = 32 * 1024
        val DETECTED = Key.create<Pair<Long, Boolean>>("nunjucks.html.detected")
        val NUNJUCKS_TAG = Regex("""\{%-?\s*(extends|block|from|import|include|set|if|for|macro|call|filter|raw|verbatim|asyncEach|asyncAll|ifAsync)\b""")
    }
}
