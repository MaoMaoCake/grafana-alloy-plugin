package com.maomaocake.grafanaalloyplugin.envfile

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.charset.StandardCharsets

/**
 * Loads and caches the project's configured envfile. Invalidated when the file's VFS modstamp
 * changes, so edits to the envfile are picked up without restarting the IDE.
 *
 * The parser is deliberately minimal — just enough of dotenv to handle the common case:
 *   KEY=value
 *   KEY="quoted value"
 *   export KEY=value
 *   # comment lines and trailing # comments ignored
 *
 * Alternative shells / multi-line values / variable interpolation / backtick substitution are
 * NOT supported. An unparseable line is silently skipped (the goal is completion suggestions,
 * not strict validation).
 */
@Service(Service.Level.PROJECT)
class AlloyEnvFile(private val project: Project) {

    private data class Cached(val stamp: Long, val entries: LinkedHashMap<String, String>)

    @Volatile private var cached: Cached? = null

    /**
     * Returns the envfile entries as a `name -> raw value` map, in file order. Empty when the
     * envfile setting is blank, the file can't be resolved, or parsing returns nothing.
     */
    fun entries(): Map<String, String> {
        val path = AlloyEnvFileSettings.getInstance(project).envFilePath.trim()
        if (path.isEmpty()) return emptyMap()
        val vf = resolve(path) ?: return emptyMap()
        val stamp = vf.modificationStamp
        val hit = cached
        if (hit != null && hit.stamp == stamp) return hit.entries

        val parsed = parse(vf)
        cached = Cached(stamp, parsed)
        return parsed
    }

    private fun resolve(path: String): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        val vfm = VirtualFileManager.getInstance()

        // Try LocalFileSystem first (normal case: user points at a path on disk).
        lfs.findFileByPath(path)?.takeIf { !it.isDirectory }?.let { return it }

        // Fall back to URL-based lookup so in-memory test filesystems work; the fixture
        // framework stores files under `temp://` which LocalFileSystem doesn't know about.
        vfm.findFileByUrl(path)?.takeIf { !it.isDirectory }?.let { return it }
        if (!path.contains("://")) {
            vfm.findFileByUrl("file://$path")?.takeIf { !it.isDirectory }?.let { return it }
        }

        // Last resort: treat path as project-relative.
        val baseDir = project.basePath ?: return null
        return lfs.findFileByPath("$baseDir/$path")?.takeIf { !it.isDirectory }
    }

    private fun parse(vf: VirtualFile): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        val text = String(vf.contentsToByteArray(), StandardCharsets.UTF_8)
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().removePrefix("export ").trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            if (!key.all { it.isLetterOrDigit() || it == '_' }) continue
            var value = line.substring(eq + 1).trim()
            // If the value opens with a quote, take the quoted segment and discard whatever
            // follows (whitespace + optional trailing comment). Otherwise, strip the first `#`
            // onward as a trailing comment.
            if (value.startsWith("\"") || value.startsWith("'")) {
                val quote = value[0]
                val closing = value.indexOf(quote, startIndex = 1)
                if (closing > 0) {
                    value = value.substring(1, closing)
                } // malformed quoted value → keep as-is, best-effort
            } else {
                val hash = value.indexOf('#')
                if (hash >= 0) value = value.substring(0, hash).trim()
            }
            out[key] = value
        }
        return out
    }

    companion object {
        fun getInstance(project: Project): AlloyEnvFile =
            project.getService(AlloyEnvFile::class.java)
    }
}
