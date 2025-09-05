package infrastructure

import common.Result
import domain.LoadoutError

interface FileSystem {
    fun readFile(path: String): Result<String, LoadoutError>
    fun writeFile(path: String, content: String): Result<Unit, LoadoutError>
    fun fileExists(path: String): Boolean
    fun createDirectory(path: String): Result<Unit, LoadoutError>
    fun listFiles(directory: String, extension: String? = null): Result<List<String>, LoadoutError>
    fun deleteFile(path: String): Result<Unit, LoadoutError>
}

expect class PlatformFileSystem() : FileSystem