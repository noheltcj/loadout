package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileRepository {
    fun readFile(path: String): Result<String, LoadoutError>

    fun writeFile(
        path: String,
        content: String,
    ): Result<Unit, LoadoutError.FileSystemError>

    fun fileExists(path: String): Boolean

    fun createDirectory(path: String): Result<Unit, LoadoutError>

    fun listFiles(
        directory: String,
        extension: String? = null,
    ): Result<List<String>, LoadoutError>

    fun deleteFile(path: String): Result<Unit, LoadoutError>
}
