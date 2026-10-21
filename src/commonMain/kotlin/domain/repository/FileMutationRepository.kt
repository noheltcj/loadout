package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileMutationRepository {
    fun writeFile(
        path: String,
        content: String,
    ): Result<Unit, LoadoutError.FileSystemError>

    /** Copies the exact bytes of [sourcePath], following symbolic links, to [destinationPath]. */
    fun copyFile(
        sourcePath: String,
        destinationPath: String,
    ): Result<Unit, LoadoutError.FileSystemError>

    fun setExecutable(path: String): Result<Unit, LoadoutError.FileSystemError>

    fun createDirectory(path: String): Result<Unit, LoadoutError>

    fun createDirectories(path: String): Result<Unit, LoadoutError>

    fun deleteFile(path: String): Result<Unit, LoadoutError>
}
