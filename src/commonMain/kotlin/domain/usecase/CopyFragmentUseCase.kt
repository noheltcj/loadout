package domain.usecase

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.AuthorizedFragmentSource
import domain.policy.AuthorizedRepositoryFilePath
import domain.policy.FragmentSource
import domain.policy.authorizeFragmentSource
import domain.policy.validateFragmentDocumentSize
import domain.policy.validateFragmentsHome
import domain.policy.validateMarkdownDestination
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.FragmentDestinationRepository
import domain.repository.RemoteFragmentSourceRepository

data class CopyFragmentInput(
    val source: FragmentSource,
    val destination: String?,
    val overwrite: Boolean,
)

data class CopyFragmentResult(
    val path: String,
)

class CopyFragmentUseCase(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val fragmentDestinationRepository: FragmentDestinationRepository,
    private val remoteFragmentSourceRepository: RemoteFragmentSourceRepository,
    private val getRepositorySettings: GetRepositorySettingsUseCase,
) {
    suspend operator fun invoke(input: CopyFragmentInput): Result<CopyFragmentResult, LoadoutError> =
        prepare(input).flatMapSuspend { preparation ->
            readSource(preparation.source, preparation.maximumBytes).flatMap { sourceBytes ->
                validateFragmentDocumentSize(sourceBytes, preparation.maximumBytes)
            }.flatMap { sourceBytes ->
                decodeDocument(preparation.source.location, sourceBytes)
            }.flatMap { content ->
                createParentDirectories(preparation.destination).flatMap {
                    fileMutationRepository.writeFile(preparation.destination.value, content)
                }
            }.map {
                CopyFragmentResult(path = preparation.destination.value)
            }
        }

    private fun prepare(input: CopyFragmentInput): Result<CopyPreparation, LoadoutError> =
        getRepositorySettings().flatMap { settings ->
            authorizeFragmentSource(input.source, settings.trustedFragmentSourceHosts).flatMap { authorizedSource ->
                val destination = input.destination ?: input.source.location.deriveSourceFileName()
                validateFragmentsHome(settings.fragmentsHome).flatMap { fragmentsHome ->
                    validateMarkdownDestination(destination, field = "destination").flatMap { validDestination ->
                        fragmentDestinationRepository.authorizeDestination(
                            fragmentsHome = fragmentsHome,
                            destination = validDestination,
                        ).flatMap { authorizedDestination ->
                            if (fileQueryRepository.fileExists(authorizedDestination.value) && !input.overwrite) {
                                Result.Error(
                                    LoadoutError.FragmentDestinationAlreadyExists(authorizedDestination.value)
                                )
                            } else {
                                Result.Success(
                                    CopyPreparation(
                                        source = authorizedSource,
                                        destination = authorizedDestination,
                                        maximumBytes = settings.maximumFragmentCopyBytes,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

    private suspend fun readSource(
        source: AuthorizedFragmentSource,
        maximumBytes: Int,
    ): Result<ByteArray, LoadoutError> =
        when (source) {
            is AuthorizedFragmentSource.LocalMarkdownFile -> readLocalSource(source.location, maximumBytes)
            is AuthorizedFragmentSource.RemoteDocument ->
                remoteFragmentSourceRepository.fetchDocument(source.url, maximumBytes)
        }

    private fun readLocalSource(
        path: String,
        maximumBytes: Int,
    ): Result<ByteArray, LoadoutError> =
        if (fileQueryRepository.isRegularFile(path)) {
            fileQueryRepository.readFileBytes(path, byteLimit = maximumBytes + 1)
        } else {
            Result.Error(LoadoutError.InvalidFragmentSource(path, "Source must be a regular file"))
        }

    private fun decodeDocument(
        source: String,
        bytes: ByteArray,
    ): Result<String, LoadoutError> =
        try {
            val content = bytes.decodeToString(throwOnInvalidSequence = true)
            if (content.isBlank()) {
                Result.Error(LoadoutError.InvalidFragmentSource(source, "Document must not be empty"))
            } else {
                Result.Success(content)
            }
        } catch (error: CharacterCodingException) {
            Result.Error(LoadoutError.InvalidFragmentSource(source, "Document must be valid UTF-8", error))
        }

    private fun createParentDirectories(destination: AuthorizedRepositoryFilePath): Result<Unit, LoadoutError> =
        fileMutationRepository.createDirectories(destination.value.substringBeforeLast('/'))

    private fun String.deriveSourceFileName(): String =
        substringBefore('?')
            .substringBefore('#')
            .replace('\\', '/')
            .substringAfterLast('/')

    private data class CopyPreparation(
        val source: AuthorizedFragmentSource,
        val destination: AuthorizedRepositoryFilePath,
        val maximumBytes: Int,
    )
}

private suspend fun <T, R, E> Result<T, E>.flatMapSuspend(transform: suspend (T) -> Result<R, E>): Result<R, E> =
    when (this) {
        is Result.Success -> transform(value)
        is Result.Error -> this
    }
