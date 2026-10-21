package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileMutationRepository {
    fun writeFile(
        path: String,
        content: String,
    ): Result<Unit, LoadoutError.FileSystemError>

    fun setExecutable(path: String): Result<Unit, LoadoutError.FileSystemError>

    fun createDirectory(path: String): Result<Unit, LoadoutError>

    fun createDirectories(path: String): Result<Unit, LoadoutError>

    fun deleteFile(path: String): Result<Unit, LoadoutError>
}
