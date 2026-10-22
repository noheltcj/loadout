package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileDigestRepository {
    fun digestFile(path: String): Result<String, LoadoutError>
}
