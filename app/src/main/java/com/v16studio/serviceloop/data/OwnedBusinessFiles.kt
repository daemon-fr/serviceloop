package com.v16studio.serviceloop.data

import java.io.File
import java.nio.file.Files

/** The complete set of durable app-owned binary roots; recovery staging and caches are separate. */
internal object OwnedBusinessFiles {
    val roots = listOf("attachments", "reports", "retained-images", "remote-results", "aggregate-reports", "transferred-evidence")

    fun resolve(base: File, relative: String): File {
        val normalized = relative.replace('\\', '/')
        require(relative == normalized && !File(relative).isAbsolute && ':' !in relative &&
            normalized.split('/').all { it.isNotBlank() && it != "." && it != ".." } &&
            normalized.substringBefore('/') in roots && '/' in normalized) { "Unsafe business-file path" }
        val root = base.canonicalFile
        val target = File(root, normalized).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "Business file escapes app storage" }
        var current = File(root, normalized.substringBefore('/'))
        for (part in normalized.substringAfter('/').split('/')) {
            require(!Files.isSymbolicLink(current.toPath())) { "Business file path traverses a link" }
            current = File(current, part)
        }
        require(!Files.isSymbolicLink(current.toPath())) { "Business file path is a link" }
        return target
    }

    fun existing(base: File): List<String> = roots.flatMap { rootName ->
        val root = File(base, rootName)
        require(!Files.isSymbolicLink(root.toPath())) { "Business root is a link" }
        if (!root.exists()) emptyList() else {
            require(root.isDirectory)
            val found = mutableListOf<String>()
            fun visit(directory: File) {
                val children = requireNotNull(directory.listFiles()) { "Cannot enumerate owned business files" }
                children.forEach { child ->
                    require(!Files.isSymbolicLink(child.toPath())) { "Business root contains a link" }
                    if (child.isDirectory) visit(child) else if (child.isFile) {
                        val relative = child.relativeTo(base).invariantSeparatorsPath
                        resolve(base, relative)
                        found += relative
                    }
                }
            }
            visit(root)
            found
        }
    }
}
