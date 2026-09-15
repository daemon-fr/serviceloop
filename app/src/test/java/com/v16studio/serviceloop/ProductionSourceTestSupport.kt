package com.v16studio.serviceloop

import java.io.File

/**
 * Source-contract tests deliberately read the production tree as one unit. They
 * must not encode which implementation file owns a stable product contract.
 */
internal fun productionKotlinSource(relativeDirectory: String = ""): String {
    return productionKotlinFiles(relativeDirectory).joinToString("\n") { it.readText() }
}

internal fun productionKotlinSourceContaining(vararg markers: String): String {
    val matches = productionKotlinFiles().filter { file ->
        val source = file.readText()
        markers.all(source::contains)
    }
    check(matches.isNotEmpty()) { "No production Kotlin source contains: ${markers.joinToString()}" }
    return matches.joinToString("\n") { it.readText() }
}

internal fun productionKotlinFunctionSource(startMarker: String, endMarker: String? = null): String {
    val source = productionKotlinSourceContaining(startMarker)
    val start = source.indexOf(startMarker)
    val end = endMarker?.let { source.indexOf(it, start + startMarker.length) }?.takeIf { it >= 0 } ?: source.length
    return source.substring(start, end)
}

private fun productionKotlinFiles(relativeDirectory: String = ""): List<File> {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
    val projectRoot = if (workingDirectory.name == "app") workingDirectory else File(workingDirectory, "app")
    return File(projectRoot, "src/main/java").resolve(relativeDirectory).walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .toList()
}

internal fun productionResourceSource(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
    val projectRoot = if (workingDirectory.name == "app") workingDirectory else File(workingDirectory, "app")
    return File(projectRoot, "src/main").resolve(relativePath).readText()
}
