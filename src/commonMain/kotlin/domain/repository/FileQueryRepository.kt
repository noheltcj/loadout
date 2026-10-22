package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileQueryRepository {
    fun readFile(path: String): Result<String, LoadoutError>

    fun readFileBytes(path: String): Result<ByteArray, LoadoutError>

    fun readFileBytes(
        path: String,
        byteLimit: Int,
    ): Result<ByteArray, LoadoutError>

    fun fileExists(path: String): Boolean

    fun isRegularFile(path: String): Boolean

    fun isSymbolicLink(path: String): Boolean

    fun canonicalPath(path: String): Result<String, LoadoutError>

    fun listFiles(
        directory: String,
        extension: String? = null,
    ): Result<List<String>, LoadoutError>
}
