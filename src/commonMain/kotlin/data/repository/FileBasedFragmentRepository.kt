package data.repository

import domain.entity.Fragment
import domain.entity.FragmentMetadata
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.EnvironmentRepository
import domain.repository.FileRepository
import domain.repository.FragmentRepository

class FileBasedFragmentRepository(
    private val fileRepository: FileRepository,
    private val environmentRepository: EnvironmentRepository,
    private val fragmentsDirectory: String = "fragments",
    private val globalFragmentsDirectory: String?,
) : FragmentRepository {
    override fun findByPath(path: String): Result<Fragment?, LoadoutError> =
        if (!fileRepository.fileExists(path)) {
            Result.Success(null)
        } else {
            fileRepository
                .readFile(path)
                .map { content ->
                    val now = environmentRepository.currentTimeMillis()
                    Fragment(
                        name = path.deriveFragmentNameFromPath(),
                        path = path,
                        content = content,
                        metadata = FragmentMetadata(createdAt = now, updatedAt = now),
                    )
                }
        }

    override fun findAll(): Result<List<Fragment>, LoadoutError> {
        val directories = listOfNotNull(fragmentsDirectory, globalFragmentsDirectory)

        return directories
            .flatMap { directory ->
                when (val result = fileRepository.listFiles(directory, "md")) {
                    is Result.Success -> result.value
                    is Result.Error -> return result
                }
            }.let { filePaths ->
                val fragments = mutableListOf<Fragment>()
                for (filePath in filePaths) {
                    when (val result = findByPath(filePath)) {
                        is Result.Success -> result.value?.let { fragments.add(it) }
                        is Result.Error -> return result
                    }
                }
                Result.Success(fragments.sortedBy { it.path })
            }
    }

    override fun loadContent(path: String): Result<String, LoadoutError> =
        if (!fileRepository.fileExists(path)) {
            Result.Error(LoadoutError.FragmentNotFound(path))
        } else {
            fileRepository
                .readFile(path)
                .mapError {
                    LoadoutError.InvalidFragment(path, it.cause)
                }
        }

    private fun String.deriveFragmentNameFromPath(): String =
        this
            .substringAfterLast("/")
            .substringBeforeLast(".")
}
