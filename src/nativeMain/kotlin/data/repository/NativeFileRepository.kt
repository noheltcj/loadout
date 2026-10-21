package data.repository

import data.filesystem.CanonicalPathResolver
import data.platform.platformCanonicalPath
import data.platform.platformIsSymbolicLink
import data.platform.platformMakeExecutable
import data.platform.platformMkdir
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.access
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.stat
import platform.posix.unlink

@OptIn(ExperimentalForeignApi::class)
class NativeFileRepository : FileQueryRepository, FileMutationRepository, CanonicalPathResolver {
    override fun readFile(path: String): Result<String, LoadoutError> {
        return try {
            val file =
                fopen(path, "r") ?: return Result.Error(
                    LoadoutError.FileSystemError("Cannot open file: $path"),
                )

            try {
                val content = StringBuilder()
                val buffer = ByteArray(1024)

                while (true) {
                    buffer.usePinned { pinned ->
                        val bytesRead = fread(pinned.addressOf(0), 1u, buffer.size.toULong(), file)
                        if (bytesRead == 0uL) return@usePinned false

                        val str = buffer.decodeToString(0, bytesRead.toInt())
                        content.append(str)
                        true
                    } || break
                }

                Result.Success(content.toString())
            } finally {
                fclose(file)
            }
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error reading file: $path", e))
        }
    }

    override fun readFileBytes(
        path: String,
        byteLimit: Int,
    ): Result<ByteArray, LoadoutError> {
        return try {
            val file =
                fopen(path, "rb") ?: return Result.Error(
                    LoadoutError.FileSystemError("Cannot open file: $path"),
                )

            try {
                val content = ByteArray(byteLimit)
                var contentSize = 0

                while (contentSize < byteLimit) {
                    val bytesRead = content.usePinned { pinned ->
                        fread(pinned.addressOf(contentSize), 1u, (byteLimit - contentSize).toULong(), file).toInt()
                    }
                    if (bytesRead == 0) break
                    contentSize += bytesRead
                }

                Result.Success(content.copyOf(contentSize))
            } finally {
                fclose(file)
            }
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error reading file: $path", e))
        }
    }

    override fun writeFile(
        path: String,
        content: String,
    ): Result<Unit, LoadoutError.FileSystemError> {
        return try {
            val file =
                fopen(path, "w") ?: return Result.Error(
                    LoadoutError.FileSystemError("Cannot create file: $path"),
                )

            try {
                val bytes = content.encodeToByteArray()
                bytes.usePinned { pinned ->
                    val written = fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                    if (written != bytes.size.toULong()) {
                        return Result.Error(LoadoutError.FileSystemError("Failed to write complete file: $path"))
                    }
                }
                Result.Success(Unit)
            } finally {
                fclose(file)
            }
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error writing file: $path", e))
        }
    }

    override fun setExecutable(path: String): Result<Unit, LoadoutError.FileSystemError> =
        try {
            if (platformMakeExecutable(path) == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(LoadoutError.FileSystemError("Failed to set executable bit on: $path"))
            }
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error setting executable bit on: $path", e))
        }

    override fun fileExists(path: String): Boolean = access(path, F_OK) == 0

    override fun isRegularFile(path: String): Boolean = fileType(path) == S_IFREG.toUInt()

    override fun isSymbolicLink(path: String): Boolean = platformIsSymbolicLink(path)

    override fun canonicalPath(path: String): Result<String, LoadoutError> =
        try {
            val resolved = platformCanonicalPath(path)
                ?: return Result.Error(LoadoutError.FileSystemError("Cannot resolve path: $path"))
            Result.Success(resolved)
        } catch (error: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error resolving path: $path", error))
        }

    override fun createDirectory(path: String): Result<Unit, LoadoutError> =
        if (fileExists(path)) {
            Result.Success(Unit)
        } else {
            try {
                val result = platformMkdir(path)
                if (result == 0) {
                    Result.Success(Unit)
                } else {
                    Result.Error(LoadoutError.FileSystemError("Failed to create directory: $path"))
                }
            } catch (e: Exception) {
                Result.Error(LoadoutError.FileSystemError("Error creating directory: $path", e))
            }
        }

    override fun createDirectories(path: String): Result<Unit, LoadoutError> {
        var currentPath = ""
        for (segment in path.split('/').filter(String::isNotBlank)) {
            currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            when (val result = createDirectory(currentPath)) {
                is Result.Success -> Unit
                is Result.Error -> return result
            }
        }
        return Result.Success(Unit)
    }

    override fun listFiles(
        directory: String,
        extension: String?,
    ): Result<List<String>, LoadoutError> {
        return try {
            val files = mutableListOf<String>()
            val dir =
                opendir(directory) ?: return Result.Error(
                    LoadoutError.FileSystemError("Cannot open directory: $directory"),
                )

            try {
                while (true) {
                    val entry = readdir(dir) ?: break
                    val name = entry.pointed.d_name.toKString()

                    if (name == "." || name == "..") continue

                    val fullPath = "$directory/$name"
                    val isFile = access(fullPath, F_OK) == 0 && !isDirectory(fullPath)

                    if (isFile) {
                        val matchesExtension = extension == null || name.endsWith(".$extension")
                        if (matchesExtension) {
                            files.add(fullPath)
                        }
                    }
                }
            } finally {
                closedir(dir)
            }

            Result.Success(files.sorted())
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error listing files in directory: $directory", e))
        }
    }

    override fun deleteFile(path: String): Result<Unit, LoadoutError> =
        try {
            val result = unlink(path)
            if (result == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(LoadoutError.FileSystemError("Failed to delete file: $path"))
            }
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error deleting file: $path", e))
        }

    private fun isDirectory(path: String): Boolean = fileType(path) == S_IFDIR.toUInt()

    private fun fileType(path: String): UInt? {
        memScoped {
            val statBuf = alloc<stat>()
            val result = stat(path, statBuf.ptr)
            if (result != 0) {
                return null
            }

            // Allowing this opt-in since this is still what we would want even across differing platform implementations.
            @OptIn(UnsafeNumber::class)
            return statBuf.st_mode.toUInt() and S_IFMT.toUInt()
        }
    }
}
