package data.filesystem

import domain.policy.isAbsolutePath
import domain.policy.normalizePathSeparators

/**
 * Resolves `.` and `..` segments without consulting the filesystem.
 *
 * A leading `/` or drive letter is kept and `..` never climbs above it. A relative path keeps the leading
 * `..` segments it cannot resolve.
 */
fun normalizePathLexically(path: String): String {
    val normalized = normalizePathSeparators(path)
    val isAbsolute = isAbsolutePath(normalized)
    val drive = if (isAbsolute && !normalized.startsWith('/')) normalized.take(2) else ""
    val root = if (isAbsolute) "$drive/" else ""
    val segments =
        normalized
            .removePrefix(drive)
            .split('/')
            .fold(emptyList<String>()) { resolved, segment ->
                when {
                    segment.isEmpty() || segment == "." -> resolved
                    segment != ".." -> resolved + segment
                    resolved.isNotEmpty() && resolved.last() != ".." -> resolved.dropLast(1)
                    root.isEmpty() -> resolved + segment
                    else -> resolved
                }
            }

    return (root + segments.joinToString("/")).ifEmpty { "." }
}
