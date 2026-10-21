package domain.policy

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

private val windowsDrivePrefix = Regex("^[A-Za-z]:")
private val safeDirectorySegment = Regex("^[A-Za-z0-9._-]+$")
private val safeFileSegment = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

const val MARKDOWN_EXTENSION = ".md"

@ConsistentCopyVisibility
data class RepositoryDirectoryPath internal constructor(val value: String)

@ConsistentCopyVisibility
data class RepositoryMarkdownPath internal constructor(val value: String)

@ConsistentCopyVisibility
data class AuthorizedRepositoryFilePath internal constructor(val value: String)

fun normalizePathSeparators(path: String): String = path.replace('\\', '/')

private fun isSafeRelativePath(
    path: String,
    segmentPattern: Regex,
): Boolean {
    val normalized = normalizePathSeparators(path)

    return normalized.isNotBlank() &&
        !normalized.startsWith('/') &&
        !windowsDrivePrefix.containsMatchIn(normalized) &&
        normalized.split('/').all { segment ->
            segment.isNotBlank() &&
                segment != "." &&
                segment != ".." &&
                segment.matches(segmentPattern)
        }
}

fun validateRepositoryDirectory(
    path: String,
    field: String,
): Result<RepositoryDirectoryPath, LoadoutError> =
    if (isSafeRelativePath(path, safeDirectorySegment)) {
        Result.Success(RepositoryDirectoryPath(normalizePathSeparators(path)))
    } else {
        Result.Error(
            LoadoutError.ValidationError(
                field = field,
                validationMessage = "Path must be a safe repository-relative directory",
            )
        )
    }

fun validateMarkdownDestination(
    path: String,
    field: String,
): Result<RepositoryMarkdownPath, LoadoutError> {
    val normalized = normalizePathSeparators(path)

    return if (normalized.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) &&
        isSafeRelativePath(normalized, safeFileSegment)
    ) {
        Result.Success(RepositoryMarkdownPath(normalized))
    } else {
        Result.Error(
            LoadoutError.ValidationError(
                field = field,
                validationMessage = "Destination must be a safe relative Markdown path",
            )
        )
    }
}
