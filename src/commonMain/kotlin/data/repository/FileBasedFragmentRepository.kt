package data.repository

import domain.entity.Fragment
import domain.entity.FragmentMetadata
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.EnvironmentRepository
import domain.repository.FileQueryRepository
import domain.repository.FragmentRepository

class FileBasedFragmentRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val environmentRepository: EnvironmentRepository,
) : FragmentRepository {
    override fun findByPath(path: String): Result<Fragment?, LoadoutError> =
        if (!fileQueryRepository.fileExists(path)) {
            Result.Success(null)
        } else {
            fileQueryRepository
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

    override fun loadContent(path: String): Result<String, LoadoutError> =
        if (!fileQueryRepository.fileExists(path)) {
            Result.Error(LoadoutError.FragmentNotFound(path))
        } else {
            fileQueryRepository
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
