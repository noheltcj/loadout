@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package data.repository

import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import e2e.platform.createTemporaryDirectory
import e2e.platform.deleteRecursively
import e2e.platform.environmentOverlay
import e2e.platform.withWorkingDirectoryAndEnvironment
import e2e.support.action
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

// An em dash takes three bytes, so a run of them straddles every power-of-two read boundary.
private const val THREE_BYTE_CHARACTER = "\u2014"
private const val THREE_BYTE_CHARACTER_COUNT = 4_000

private data class CreateDirectoriesObservation(
    val result: Result<Unit, LoadoutError>,
    val existsAtAbsolutePath: Boolean,
    val existsUnderWorkingDirectory: Boolean,
)

class NativeFileRepositorySmokeSpec : BehaviorSpec({
    val repository = NativeFileRepository()
    val workingDirectory = createTemporaryDirectory("loadout-native-working-directory")
    val targetRoot = createTemporaryDirectory("loadout-native-target")

    afterSpec {
        deleteRecursively(workingDirectory)
        deleteRecursively(targetRoot)
    }

    context("native file repository smoke spec") {
        given("an absolute directory path whose parents do not exist yet") {
            val absoluteDirectory = "$targetRoot/nested/deeper"

            action("createDirectories is run from an unrelated working directory") {
                val result =
                    withWorkingDirectoryAndEnvironment(workingDirectory, environmentOverlay()) {
                        repository.createDirectories(absoluteDirectory)
                    }
                val observation =
                    CreateDirectoriesObservation(
                        result = result,
                        existsAtAbsolutePath = repository.fileExists(absoluteDirectory),
                        existsUnderWorkingDirectory =
                        repository.fileExists("$workingDirectory/${absoluteDirectory.removePrefix("/")}"),
                    )

                then("it succeeds") {
                    observation.result.shouldBeInstanceOf<Result.Success<Unit>>()
                }

                then("it creates the directory at the absolute path") {
                    observation.existsAtAbsolutePath shouldBe true
                }

                then("it creates nothing under the working directory") {
                    observation.existsUnderWorkingDirectory shouldBe false
                }
            }
        }

        given("a UTF-8 file of three-byte characters longer than one read") {
            val filePath = "$targetRoot/three-byte-characters.md"
            val content = THREE_BYTE_CHARACTER.repeat(THREE_BYTE_CHARACTER_COUNT)
            check(repository.writeFile(filePath, content) is Result.Success) { "Could not seed $filePath" }

            action("readFile is run") {
                val readResult = repository.readFile(filePath)

                then("it returns the content unchanged") {
                    readResult shouldBe Result.Success(content)
                }
            }
        }
    }
})
