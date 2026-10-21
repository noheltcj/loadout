@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.OutputFilePath
import domain.policy.RepositoryDirectoryPath
import domain.policy.RepositoryMarkdownPath
import domain.policy.validateMarkdownDestination
import domain.policy.validateOutputFilePath
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

    Given("a Markdown output file at the repository root") {
        val outputFile = "CLAUDE.md"

        When("the path is validated as an output file") {
            val validation = validateOutputFilePath(outputFile, "output-files")

            Then("it preserves the repository-relative output file") {
                validation.acceptedPath<OutputFilePath>().value shouldBe outputFile
            }
        }
    }

    Given("a Markdown output file inside a hidden agent directory") {
        val outputFile = ".github/copilot-instructions.md"
        val field = "output-files"

        When("the path is validated as an output file") {
            val validation = validateOutputFilePath(outputFile, field)

            Then("it accepts the hidden parent directory") {
                validation.acceptedPath<OutputFilePath>().value shouldBe outputFile
            }
        }

        Given("the path uses Windows separators") {
            val windowsPath = outputFile.replace('/', '\\')

            When("the path is validated as an output file") {
                val validation = validateOutputFilePath(windowsPath, field)

                Then("it normalizes output file separators") {
                    validation.acceptedPath<OutputFilePath>().value shouldBe outputFile
                }
            }
        }

        Given("the Markdown extension uses uppercase letters") {
            val uppercasePath = outputFile.removeSuffix(".md") + ".MD"

            When("the path is validated as an output file") {
                val validation = validateOutputFilePath(uppercasePath, field)

                Then("it preserves the valid uppercase extension") {
                    validation.acceptedPath<OutputFilePath>().value shouldBe uppercasePath
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

            When("the path is validated as an output file") {
                val validation = validateOutputFilePath(unsafePath, field)

                thenPathIsRejected(validation, field, "must be a repository-relative Markdown file")
            }
        }
    }

    mapOf(
        "an output file without a Markdown extension" to ".cursor/rules/loadout.mdc",
        "an output file whose name starts with a dot" to "docs/.hidden.md",
    ).forEach { (description, outputFile) ->
        Given(description) {
            val field = "output-files"

            When("the path is validated as an output file") {
                val validation = validateOutputFilePath(outputFile, field)

                thenPathIsRejected(validation, field, "must be a repository-relative Markdown file")
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
