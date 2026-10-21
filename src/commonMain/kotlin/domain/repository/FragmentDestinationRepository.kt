package domain.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.AuthorizedRepositoryFilePath
import domain.policy.RepositoryDirectoryPath
import domain.policy.RepositoryMarkdownPath

interface FragmentDestinationRepository {
    fun authorizeDestination(
        fragmentsHome: RepositoryDirectoryPath,
        destination: RepositoryMarkdownPath,
    ): Result<AuthorizedRepositoryFilePath, LoadoutError>
}
