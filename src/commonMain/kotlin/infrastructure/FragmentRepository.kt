package infrastructure

import common.Result
import domain.Fragment
import domain.LoadoutError

interface FragmentRepository {
    fun findByPath(path: String): Result<Fragment?, LoadoutError>
    fun findAll(): Result<List<Fragment>, LoadoutError>
    fun loadContent(path: String): Result<String, LoadoutError>
}

class FileBasedFragmentRepository(
    private val fileSystem: FileSystem,
    private val fragmentsDirectory: String = "fragments"
) : FragmentRepository {
    
    override fun findByPath(path: String): Result<Fragment?, LoadoutError> {
        val fullPath = resolveFragmentPath(path)
        
        return if (!fileSystem.fileExists(fullPath)) {
            Result.Success(null)
        } else {
            fileSystem.readFile(fullPath)
                .map { content ->
                    Fragment(
                        path = path,
                        content = content
                    )
                }
        }
    }
    
    override fun findAll(): Result<List<Fragment>, LoadoutError> {
        return fileSystem.listFiles(fragmentsDirectory, "md")
            .flatMap { files ->
                val fragments = mutableListOf<Fragment>()
                for (file in files) {
                    val relativePath = file.removePrefix("$fragmentsDirectory/")
                    when (val result = findByPath(relativePath)) {
                        is Result.Success -> result.value?.let { fragments.add(it) }
                        is Result.Error -> return result
                    }
                }
                Result.Success(fragments.sortedBy { it.path })
            }
    }
    
    override fun loadContent(path: String): Result<String, LoadoutError> {
        val fullPath = resolveFragmentPath(path)
        
        return if (!fileSystem.fileExists(fullPath)) {
            Result.Error(LoadoutError.FragmentNotFound(path))
        } else {
            fileSystem.readFile(fullPath)
                .mapError { 
                    LoadoutError.InvalidFragment(path, it.cause)
                }
        }
    }
    
    private fun resolveFragmentPath(path: String): String {
        return if (path.startsWith("/") || path.contains(":/")) {
            path
        } else {
            "$fragmentsDirectory/$path"
        }
    }
}