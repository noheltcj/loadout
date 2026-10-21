package data.filesystem

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface CanonicalPathResolver {
    fun fileExists(path: String): Boolean

    fun isSymbolicLink(path: String): Boolean

    fun canonicalPath(path: String): Result<String, LoadoutError>
}
