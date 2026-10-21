package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result

interface FileQueryRepository {
    fun readFile(path: String): Result<String, LoadoutError>

    /** Reads the exact bytes of [path], following symbolic links. */
    fun readFileBytes(path: String): Result<ByteArray, LoadoutError>

    /** Reads at most [byteLimit] leading bytes of [path], following symbolic links. */
    fun readFileBytes(
        path: String,
        byteLimit: Int,
    ): Result<ByteArray, LoadoutError>

    fun fileExists(path: String): Boolean

    fun isRegularFile(path: String): Boolean

    fun isSymbolicLink(path: String): Boolean

    /** The absolute path [path] resolves to after following every symbolic link. */
    fun canonicalPath(path: String): Result<String, LoadoutError>

    fun listFiles(
        directory: String,
        extension: String? = null,
    ): Result<List<String>, LoadoutError>
}
