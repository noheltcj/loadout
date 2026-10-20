package data.repository

import data.filesystem.CanonicalPathResolver
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.AuthorizedRepositoryFilePath
import domain.policy.RepositoryDirectoryPath
import domain.policy.RepositoryMarkdownPath
import domain.policy.normalizePathSeparators
import domain.repository.FragmentDestinationRepository

class FileSystemFragmentDestinationRepository(
    private val pathResolver: CanonicalPathResolver,
) : FragmentDestinationRepository {
    /**
     * Both the fragments home and the nearest existing destination ancestor must resolve inside the
     * repository. When the fragments home already exists, the destination must also resolve below that
     * exact canonical directory.
     */
    override fun authorizeDestination(
        fragmentsHome: RepositoryDirectoryPath,
        destination: RepositoryMarkdownPath,
    ): Result<AuthorizedRepositoryFilePath, LoadoutError> {
        val destinationPath = "${fragmentsHome.value}/${destination.value}"
        if (pathResolver.isSymbolicLink(destinationPath)) {
            return unsafeDestination(destinationPath)
        }

        return resolveCanonicalPath(".").flatMap { repositoryRoot ->
            resolveCanonicalPath(nearestExistingAncestor(fragmentsHome.value)).flatMap { fragmentsAncestor ->
                if (!isPathWithin(fragmentsAncestor, repositoryRoot)) {
                    unsafeDestination(destinationPath)
                } else {
                    authorizeDestinationAncestor(
                        destinationPath = destinationPath,
                        fragmentsHome = fragmentsHome,
                        fragmentsAncestor = fragmentsAncestor,
                    )
                }
            }
        }
    }

    private fun authorizeDestinationAncestor(
        destinationPath: String,
        fragmentsHome: RepositoryDirectoryPath,
        fragmentsAncestor: String,
    ): Result<AuthorizedRepositoryFilePath, LoadoutError> =
        resolveCanonicalPath(nearestExistingAncestor(destinationPath)).flatMap { destinationAncestor ->
            val requiredParentResult =
                if (pathResolver.fileExists(fragmentsHome.value)) {
                    resolveCanonicalPath(fragmentsHome.value)
                } else {
                    Result.Success(fragmentsAncestor)
                }

            requiredParentResult.flatMap { requiredParent ->
                if (isPathWithin(destinationAncestor, requiredParent)) {
                    Result.Success(AuthorizedRepositoryFilePath(destinationPath))
                } else {
                    unsafeDestination(destinationPath)
                }
            }
        }

    private fun resolveCanonicalPath(path: String): Result<String, LoadoutError> =
        pathResolver.canonicalPath(path).map { canonicalPath ->
            val normalized = normalizePathSeparators(canonicalPath)
            if (normalized == "/") normalized else normalized.trimEnd('/')
        }

    private fun nearestExistingAncestor(path: String): String {
        var candidate = path
        while (true) {
            if (pathResolver.fileExists(candidate)) return candidate
            if (!candidate.contains('/')) return "."
            candidate = candidate.substringBeforeLast('/')
        }
    }

    private fun isPathWithin(
        candidate: String,
        parent: String,
    ): Boolean = candidate == parent || candidate.startsWith(if (parent == "/") parent else "$parent/")

    private fun unsafeDestination(path: String): Result<AuthorizedRepositoryFilePath, LoadoutError> =
        Result.Error(
            LoadoutError.ValidationError(
                field = "destination",
                validationMessage = "Destination must remain inside the configured fragments home: $path",
            )
        )
}
