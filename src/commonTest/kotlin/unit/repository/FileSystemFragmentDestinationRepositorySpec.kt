@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.repository

import data.filesystem.CanonicalPathResolver
import data.repository.FileSystemFragmentDestinationRepository
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.AuthorizedRepositoryFilePath
import domain.policy.validateMarkdownDestination
import domain.policy.validateRepositoryDirectory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private data class RepositoryFileSystem(
    private val existingPaths: Set<String>,
    private val symbolicLinks: Set<String> = emptySet(),
    private val canonicalPaths: Map<String, Result<String, LoadoutError>>,
) : CanonicalPathResolver {
    fun withPath(
        path: String,
        canonicalPath: String,
    ): RepositoryFileSystem =
        copy(
            existingPaths = existingPaths + path,
            canonicalPaths = canonicalPaths + (path to Result.Success(canonicalPath)),
        )

    fun withSymbolicLink(
        path: String,
        targetPath: String,
    ): RepositoryFileSystem = withPath(path, targetPath).copy(symbolicLinks = symbolicLinks + path)

    fun withDanglingSymbolicLink(path: String): RepositoryFileSystem =
        copy(
            existingPaths = existingPaths - path,
            symbolicLinks = symbolicLinks + path,
            canonicalPaths = canonicalPaths - path,
        )

    fun withResolutionFailure(
        path: String,
        error: LoadoutError,
    ): RepositoryFileSystem =
        copy(
            existingPaths = existingPaths + path,
            canonicalPaths = canonicalPaths + (path to Result.Error(error)),
        )

    override fun fileExists(path: String): Boolean = path in existingPaths

    override fun isSymbolicLink(path: String): Boolean = path in symbolicLinks

    override fun canonicalPath(path: String): Result<String, LoadoutError> =
        canonicalPaths[path] ?: Result.Error(LoadoutError.FileSystemError("No canonical path for $path"))
}

private fun <T> Result<T, LoadoutError>.acceptedPath(): T = (this as Result.Success).value

private fun <T> Result<T, LoadoutError>.error(): LoadoutError = (this as Result.Error).error

private suspend fun BehaviorSpecWhenContainerScope.thenDestinationIsRejected(
    authorization: Result<AuthorizedRepositoryFilePath, LoadoutError>,
    destinationPath: String,
) {
    Then("it identifies the destination as the invalid field") {
        (authorization.error() as LoadoutError.ValidationError).field shouldBe "destination"
    }
    Then("it explains the fragments-home containment requirement") {
        (authorization.error() as LoadoutError.ValidationError).validationMessage.shouldContain("must remain inside")
    }
    Then("it identifies the rejected repository-relative destination") {
        (authorization.error() as LoadoutError.ValidationError).validationMessage.shouldContain(destinationPath)
    }
}

class FileSystemFragmentDestinationRepositorySpec : BehaviorSpec({
    Given("a repository has a fragments home configured inside it") {
        val repositoryFileSystem =
            RepositoryFileSystem(
                existingPaths = setOf("."),
                canonicalPaths = mapOf("." to Result.Success("/repo")),
            )
        val fragmentsHome = validateRepositoryDirectory("fragments", "fragments-home").acceptedPath()
        val destination = validateMarkdownDestination("nested/copied.md", "destination").acceptedPath()
        val destinationPath = "fragments/nested/copied.md"
        val repository = FileSystemFragmentDestinationRepository(repositoryFileSystem)

        When("a nested destination is authorized before the fragments home exists") {
            val authorization = repository.authorizeDestination(fragmentsHome, destination)

            Then("it authorizes the full repository-relative destination") {
                authorization.acceptedPath().value shouldBe destinationPath
            }
        }

        Given("the fragments home already exists in the repository") {
            val existingHome = repositoryFileSystem.withPath("fragments", "/repo/fragments")
            val existingHomeRepository = FileSystemFragmentDestinationRepository(existingHome)

            When("a nested Markdown destination is authorized") {
                val authorization = existingHomeRepository.authorizeDestination(fragmentsHome, destination)

                Then("it authorizes the missing destination under the existing home") {
                    authorization.acceptedPath().value shouldBe destinationPath
                }
            }

            Given("the destination is an existing regular file") {
                val existingDestinationRepository = FileSystemFragmentDestinationRepository(
                    existingHome.withPath(destinationPath, "/repo/fragments/nested/copied.md")
                )

                When("the existing Markdown destination is authorized") {
                    val authorization = existingDestinationRepository.authorizeDestination(fragmentsHome, destination)

                    Then("it authorizes the existing contained file") {
                        authorization.acceptedPath().value shouldBe destinationPath
                    }
                }
            }

            mapOf(
                "the nested directory links to another repository directory" to "/repo/other",
                "the nested directory links to a sibling with the fragments-home name as a prefix" to
                    "/repo/fragments-outside",
            ).forEach { (description, targetPath) ->
                Given(description) {
                    val linkedRepository = FileSystemFragmentDestinationRepository(
                        existingHome.withSymbolicLink("fragments/nested", targetPath)
                    )

                    When("a Markdown destination under the symbolic link is authorized") {
                        val authorization = linkedRepository.authorizeDestination(fragmentsHome, destination)

                        thenDestinationIsRejected(authorization, destinationPath)
                    }
                }
            }

            Given("the nested directory links within the fragments home") {
                val linkedRepository = FileSystemFragmentDestinationRepository(
                    existingHome.withSymbolicLink("fragments/nested", "/repo/fragments/shared")
                )

                When("a Markdown destination under the symbolic link is authorized") {
                    val authorization = linkedRepository.authorizeDestination(fragmentsHome, destination)

                    Then("it preserves the requested path through the contained directory link") {
                        authorization.acceptedPath().value shouldBe destinationPath
                    }
                }
            }

            Given("the canonical destination ancestor cannot be resolved") {
                val failure = LoadoutError.FileSystemError("Cannot resolve nested directory")
                val failingRepository = FileSystemFragmentDestinationRepository(
                    existingHome.withResolutionFailure("fragments/nested", failure)
                )

                When("the nested destination is authorized") {
                    val authorization = failingRepository.authorizeDestination(fragmentsHome, destination)

                    Then("it propagates the destination ancestor resolution failure") {
                        authorization.error() shouldBe failure
                    }
                }
            }
        }

        mapOf(
            "the fragments home links outside the repository" to "/outside",
            "the fragments home links to a sibling with the repository name as a prefix" to "/repo-outside",
        ).forEach { (description, targetPath) ->
            Given(description) {
                val linkedRepository = FileSystemFragmentDestinationRepository(
                    repositoryFileSystem.withSymbolicLink("fragments", targetPath)
                )

                When("the nested Markdown destination is authorized") {
                    val authorization = linkedRepository.authorizeDestination(fragmentsHome, destination)

                    thenDestinationIsRejected(authorization, destinationPath)
                }
            }
        }

        Given("the fragments home links to a directory inside the repository") {
            val linkedRepository = FileSystemFragmentDestinationRepository(
                repositoryFileSystem.withSymbolicLink("fragments", "/repo/shared")
            )

            When("the nested destination is authorized") {
                val authorization = linkedRepository.authorizeDestination(fragmentsHome, destination)

                Then("it authorizes the original repository-relative destination") {
                    authorization.acceptedPath().value shouldBe destinationPath
                }
            }
        }

        mapOf(
            "the destination file links within the fragments home" to "/repo/fragments/original.md",
            "the destination file links outside the repository" to "/outside/copied.md",
        ).forEach { (description, targetPath) ->
            Given(description) {
                val linkedRepository = FileSystemFragmentDestinationRepository(
                    repositoryFileSystem.withSymbolicLink(destinationPath, targetPath)
                )

                When("the symbolic-link destination is authorized") {
                    val authorization = linkedRepository.authorizeDestination(fragmentsHome, destination)

                    thenDestinationIsRejected(authorization, destinationPath)
                }
            }
        }

        Given("the destination file is a dangling symbolic link") {
            val linkedRepository = FileSystemFragmentDestinationRepository(
                repositoryFileSystem.withDanglingSymbolicLink(destinationPath)
            )

            When("the dangling symbolic-link destination is authorized") {
                val authorization = linkedRepository.authorizeDestination(fragmentsHome, destination)

                thenDestinationIsRejected(authorization, destinationPath)
            }
        }

        mapOf(
            "the repository root cannot be resolved" to ".",
            "the fragments home cannot be resolved" to "fragments",
        ).forEach { (description, failingPath) ->
            Given(description) {
                val failure = LoadoutError.FileSystemError("Cannot resolve $failingPath")
                val failingRepository = FileSystemFragmentDestinationRepository(
                    repositoryFileSystem.withResolutionFailure(failingPath, failure)
                )

                When("the nested destination is authorized") {
                    val authorization = failingRepository.authorizeDestination(fragmentsHome, destination)

                    Then("it propagates the canonical path resolution failure") {
                        authorization.error() shouldBe failure
                    }
                }
            }
        }

        Given("a custom fragments home is nested below an escaping directory link") {
            val nestedHome = validateRepositoryDirectory("guidance/library", "fragments-home").acceptedPath()
            val linkedRepository = FileSystemFragmentDestinationRepository(
                repositoryFileSystem.withSymbolicLink("guidance", "/outside")
            )

            When("the nested destination is authorized before the custom fragments home exists") {
                val authorization = linkedRepository.authorizeDestination(nestedHome, destination)

                thenDestinationIsRejected(authorization, "guidance/library/nested/copied.md")
            }
        }

        mapOf(
            "canonical paths use Windows separators and trailing separators" to "C:\\repo\\",
            "the repository is the filesystem root" to "/",
        ).forEach { (description, canonicalRoot) ->
            Given(description) {
                val normalizedRepository = FileSystemFragmentDestinationRepository(
                    repositoryFileSystem
                        .withPath(".", canonicalRoot)
                        .withPath("fragments", if (canonicalRoot == "/") "/fragments/" else "C:/repo/fragments/")
                )

                When("the nested destination is authorized") {
                    val authorization = normalizedRepository.authorizeDestination(fragmentsHome, destination)

                    Then("it authorizes the repository-relative destination") {
                        authorization.acceptedPath().value shouldBe destinationPath
                    }
                }
            }
        }
    }
})
