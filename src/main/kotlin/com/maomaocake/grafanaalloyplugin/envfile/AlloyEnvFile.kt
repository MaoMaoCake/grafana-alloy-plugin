package com.maomaocake.grafanaalloyplugin.envfile

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

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
        if (vf.length > MAX_ENV_FILE_BYTES) {
            LOG.warn("Envfile $path exceeds ${MAX_ENV_FILE_BYTES}B cap (${vf.length}B); ignoring to avoid OOM")
            return emptyMap()
        }
        val stamp = vf.modificationStamp
        val hit = cached
        if (hit != null && hit.stamp == stamp) return hit.entries

        val parsed = parse(vf)
        cached = Cached(stamp, parsed)
        return parsed
    }

    /**
     * Resolves the configured envfile path and verifies it lives under the project root.
     * The envfile setting is **project-level**, which means a `.idea/grafanaAlloy.xml`
     * checked into a hostile repo could otherwise point at e.g. `~/.aws/credentials` and
     * leak the file's keys (and values, when show-values is on) via our completion popup.
     * Restricting to `project.basePath` mirrors how IntelliJ's own project-file settings
     * are scoped.
     *
     * `temp://` URLs are permitted so the unit-test fixture filesystem keeps working.
     */
    private fun resolve(path: String): VirtualFile? {
        val lfs = LocalFileSystem.getInstance()
        val vfm = VirtualFileManager.getInstance()
        val baseDir = project.basePath

        // Try LocalFileSystem first (normal case: user points at a path on disk).
        val direct = lfs.findFileByPath(path)?.takeIf { !it.isDirectory }
        if (direct != null) return if (isUnderProject(direct, baseDir)) direct else refuseOutsideProject(direct)

        // `temp://` URLs (unit test fixture) and explicit `file://` URLs.
        val urlHit = vfm.findFileByUrl(path)?.takeIf { !it.isDirectory }
            ?: if (!path.contains("://")) vfm.findFileByUrl("file://$path")?.takeIf { !it.isDirectory } else null
        if (urlHit != null) {
            // `temp://` files used only in tests: always allow. Real-filesystem URLs get the
            // under-project check.
            val protocol = urlHit.fileSystem.protocol
            if (protocol != "file" || isUnderProject(urlHit, baseDir)) return urlHit
            return refuseOutsideProject(urlHit)
        }

        // Last resort: treat as project-relative.
        if (baseDir == null) return null
        val relative = lfs.findFileByPath("$baseDir/$path")?.takeIf { !it.isDirectory }
        return relative?.takeIf { isUnderProject(it, baseDir) }
    }

    private fun isUnderProject(vf: VirtualFile, baseDir: String?): Boolean {
        if (baseDir == null) return false
        val basePath: Path = try {
            Paths.get(baseDir).toAbsolutePath().normalize()
        } catch (_: Throwable) {
            return false
        }
        val filePath: Path = try {
            Paths.get(vf.path).toAbsolutePath().normalize()
        } catch (_: Throwable) {
            return false
        }
        return filePath.startsWith(basePath)
    }

    private fun refuseOutsideProject(vf: VirtualFile): VirtualFile? {
        LOG.warn("Refusing envfile ${vf.path}: outside project root; edit the path in Settings → Alloy")
        return null
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
        private val LOG = Logger.getInstance(AlloyEnvFile::class.java)

        /** Refuse to parse envfiles larger than ~1 MiB — anything bigger isn't a real dotenv. */
        private const val MAX_ENV_FILE_BYTES = 1L * 1024 * 1024

        fun getInstance(project: Project): AlloyEnvFile =
            project.getService(AlloyEnvFile::class.java)
    }
}
