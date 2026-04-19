package domain.policy

import domain.entity.Loadout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

fun validateLoadoutName(name: String): Result<String, LoadoutError> =
    if (name.isBlank()) {
        Result.Error(LoadoutError.ValidationError("name", "Name cannot be blank"))
    } else if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
        Result.Error(
            LoadoutError.ValidationError(
                "name",
                "Name must contain only alphanumeric characters, underscores, and hyphens"
            )
        )
    } else {
        Result.Success(name)
    }

fun validateDescription(description: String?): Result<String?, LoadoutError> =
    if (description != null && description.isBlank()) {
        Result.Error(LoadoutError.ValidationError("description", "Description cannot be blank if provided"))
    } else {
        Result.Success(description)
    }

fun validateMarkdownFragmentPath(path: String): Result<String, LoadoutError> {
    val normalizedPath = normalizeFragmentPath(path)
    return if (!normalizedPath.endsWith(".md", ignoreCase = true)) {
        Result.Error(LoadoutError.ValidationError("fragment", "Fragment must be a markdown file (.md)"))
    } else {
        Result.Success(normalizedPath)
    }
}

fun validateMarkdownFragmentPaths(paths: List<String>): Result<List<String>, LoadoutError> {
    val normalizedPaths = mutableListOf<String>()

    for (path in paths) {
        when (val result = validateMarkdownFragmentPath(path)) {
            is Result.Success -> normalizedPaths += result.value
            is Result.Error -> return result
        }
    }

    return Result.Success(normalizedPaths)
}

fun validateNoDuplicateFragments(normalizedPaths: List<String>): Result<Unit, LoadoutError> {
    val duplicates = normalizedPaths.groupingBy { it }.eachCount().filter { it.value > 1 }

    return if (duplicates.isNotEmpty()) {
        Result.Error(
            LoadoutError.ValidationError(
                "fragment",
                "Duplicate fragments provided: ${duplicates.keys.joinToString()}"
            )
        )
    } else {
        Result.Success(Unit)
    }
}

fun normalizeStoredLoadout(loadout: Loadout): Loadout =
    loadout.copy(fragments = loadout.fragments.map(::normalizeFragmentPath))

fun normalizeFragmentPath(path: String): String =
    path
        .replace(Regex("(^\\./)+"), "")
        .replace(Regex("/\\./"), "/")
        .replace(Regex("/{2,}"), "/")
