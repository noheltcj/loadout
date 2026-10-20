@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.RepositoryDirectoryPath
import domain.policy.RepositoryMarkdownPath
import domain.policy.validateMarkdownDestination
import domain.policy.validateRepositoryDirectory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun <T> Result<T, LoadoutError>.acceptedPath(): T = (this as Result.Success).value

private fun <T> Result<T, LoadoutError>.rejection(): LoadoutError.ValidationError =
    (this as Result.Error).error as LoadoutError.ValidationError

private suspend fun <T> BehaviorSpecWhenContainerScope.thenPathIsRejected(
    validation: Result<T, LoadoutError>,
    field: String,
    reason: String,
) {
    Then("it identifies the caller-supplied validation field") {
        validation.rejection().field shouldBe field
    }
    Then("it explains the required safe path shape") {
        validation.rejection().validationMessage.shouldContain(reason)
    }
}

class RepositoryPathSpec : BehaviorSpec({
    Given("a nested repository-relative directory path") {
        val directoryPath = "guidance/library"
        val field = "custom-directory"

        When("the path is validated as a repository directory") {
            val validation = validateRepositoryDirectory(directoryPath, field)

            Then("it preserves the repository-relative directory") {
                validation.acceptedPath<RepositoryDirectoryPath>().value shouldBe directoryPath
            }
        }

        Given("the path uses Windows separators") {
            val windowsPath = directoryPath.replace('/', '\\')

            When("the path is validated as a repository directory") {
                val validation = validateRepositoryDirectory(windowsPath, field)

                Then("it normalizes directory separators") {
                    validation.acceptedPath<RepositoryDirectoryPath>().value shouldBe directoryPath
                }
            }
        }
    }

    Given("a hidden repository directory") {
        val directoryPath = ".guidance/library"

        When("the path is validated as a repository directory") {
            val validation = validateRepositoryDirectory(directoryPath, "directory")

            Then("it accepts hidden directory names") {
                validation.acceptedPath<RepositoryDirectoryPath>().value shouldBe directoryPath
            }
        }
    }

    Given("a nested repository-relative Markdown path") {
        val markdownPath = "testing/copied.md"
        val field = "custom-destination"

        When("the path is validated as a Markdown destination") {
            val validation = validateMarkdownDestination(markdownPath, field)

            Then("it preserves the repository-relative Markdown destination") {
                validation.acceptedPath<RepositoryMarkdownPath>().value shouldBe markdownPath
            }
        }

        Given("the path uses Windows separators") {
            val windowsPath = markdownPath.replace('/', '\\')

            When("the path is validated as a Markdown destination") {
                val validation = validateMarkdownDestination(windowsPath, field)

                Then("it normalizes destination separators") {
                    validation.acceptedPath<RepositoryMarkdownPath>().value shouldBe markdownPath
                }
            }
        }

        Given("the Markdown extension uses uppercase letters") {
            val uppercasePath = markdownPath.removeSuffix(".md") + ".MD"

            When("the path is validated as a Markdown destination") {
                val validation = validateMarkdownDestination(uppercasePath, field)

                Then("it preserves the valid uppercase extension") {
                    validation.acceptedPath<RepositoryMarkdownPath>().value shouldBe uppercasePath
                }
            }
        }
    }

    mapOf(
        "a blank path" to "",
        "an absolute POSIX path" to "/outside/copied.md",
        "an absolute Windows path" to "C:/outside/copied.md",
        "a Windows drive-relative path" to "C:copied.md",
        "a Windows UNC path" to "\\\\server\\share\\copied.md",
        "a path traversing above the repository" to "../copied.md",
        "a path with interior traversal" to "nested/../../copied.md",
        "a path with a current-directory segment" to "nested/./copied.md",
        "a path with an empty segment" to "nested//copied.md",
        "a path containing spaces" to "nested/my guidance.md",
    ).forEach { (description, unsafePath) ->
        Given(description) {
            val field = "requested-path"

            When("the path is validated as a repository directory") {
                val validation = validateRepositoryDirectory(unsafePath, field)

                thenPathIsRejected(validation, field, "safe repository-relative directory")
            }

            When("the path is validated as a Markdown destination") {
                val validation = validateMarkdownDestination(unsafePath, field)

                thenPathIsRejected(validation, field, "safe relative Markdown path")
            }
        }
    }

    mapOf(
        "a destination without a Markdown extension" to "testing/copied.txt",
        "a destination containing a hidden segment" to ".guidance/copied.md",
    ).forEach { (description, destinationPath) ->
        Given(description) {
            val field = "destination"

            When("the path is validated as a Markdown destination") {
                val validation = validateMarkdownDestination(destinationPath, field)

                thenPathIsRejected(validation, field, "safe relative Markdown path")
            }
        }
    }
})
