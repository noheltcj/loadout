package infrastructure

import common.Result
import domain.LoadoutError
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual class PlatformFileSystem actual constructor() : FileSystem {
    
    override fun readFile(path: String): Result<String, LoadoutError> {
        return try {
            val file = fopen(path, "r") ?: return Result.Error(
                LoadoutError.FileSystemError("Cannot open file: $path")
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
    
    override fun writeFile(path: String, content: String): Result<Unit, LoadoutError> {
        return try {
            val file = fopen(path, "w") ?: return Result.Error(
                LoadoutError.FileSystemError("Cannot create file: $path")
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
    
    override fun fileExists(path: String): Boolean {
        return access(path, F_OK) == 0
    }
    
    override fun createDirectory(path: String): Result<Unit, LoadoutError> {
        return if (fileExists(path)) {
            Result.Success(Unit)
        } else {
            try {
                val result = mkdir(path, (S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH).toUShort())
                if (result == 0) {
                    Result.Success(Unit)
                } else {
                    Result.Error(LoadoutError.FileSystemError("Failed to create directory: $path"))
                }
            } catch (e: Exception) {
                Result.Error(LoadoutError.FileSystemError("Error creating directory: $path", e))
            }
        }
    }
    
    override fun listFiles(directory: String, extension: String?): Result<List<String>, LoadoutError> {
        return try {
            val files = mutableListOf<String>()
            val dir = opendir(directory) ?: return Result.Error(
                LoadoutError.FileSystemError("Cannot open directory: $directory")
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
    
    override fun deleteFile(path: String): Result<Unit, LoadoutError> {
        return try {
            val result = unlink(path)
            if (result == 0) {
                Result.Success(Unit)
            } else {
                Result.Error(LoadoutError.FileSystemError("Failed to delete file: $path"))
            }
        } catch (e: Exception) {
            Result.Error(LoadoutError.FileSystemError("Error deleting file: $path", e))
        }
    }
    
    private fun isDirectory(path: String): Boolean {
        memScoped {
            val stat = alloc<stat>()
            if (stat(path, stat.ptr) != 0) return false
            return (stat.st_mode.toUInt() and S_IFMT.toUInt()) == S_IFDIR.toUInt()
        }
    }
}