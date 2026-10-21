@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.platform

import e2e.support.action
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class NativeProcessEnvironmentSafetySpec : BehaviorSpec({
    context("native process environment safety spec") {
        given("an inherited git environment exists") {
            val inheritedEnvironment =
                mapOf(
                    "GIT_DIR" to "/host/repo/.git",
                    "GIT_WORK_TREE" to "/host/repo",
                )
            val requestedWorkingDirectory = "/tmp/workspace"
            val environment = environmentOverlay {
                unset("GIT_DIR", "GIT_WORK_TREE")
            }
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = "/host/repo",
                    initialEnvironment = inheritedEnvironment,
                )

            action("execution unsets the inherited git variables") {
                val observedContext =
                    withWorkingDirectoryAndEnvironment(
                        operations = operations,
                        workingDirectory = requestedWorkingDirectory,
                        environment = environment,
                    ) {
                        ProcessContextSnapshot(
                            workingDirectory = operations.currentWorkingDirectory(),
                            environment = operations.environment.toMap(),
                        )
                    }

                then("it clears GIT_DIR during execution") {
                    observedContext.environment["GIT_DIR"] shouldBe null
                }

                then("it clears GIT_WORK_TREE during execution") {
                    observedContext.environment["GIT_WORK_TREE"] shouldBe null
                }

                then("it runs the block in the requested working directory") {
                    observedContext.workingDirectory shouldBe requestedWorkingDirectory
                }

                then("it restores the inherited git environment afterward") {
                    operations.environment shouldContainExactly inheritedEnvironment
                }
            }
        }

        given("restoring the original working directory fails") {
            val originalWorkingDirectory = "/host/repo"
            val inheritedEnvironment =
                mapOf(
                    "HOME" to "/host/home",
                    "XDG_DATA_HOME" to "/host/data",
                )
            val requestedWorkingDirectory = "/tmp/workspace"
            val environment = environmentOverlay {
                "HOME" setTo "/tmp/home"
                "XDG_DATA_HOME" setTo "/tmp/data"
            }
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = originalWorkingDirectory,
                    initialEnvironment = inheritedEnvironment,
                    failingChangeDirectories = setOf(originalWorkingDirectory),
                )

            action("the process context is cleaned up") {
                val execution = runCatching {
                    withWorkingDirectoryAndEnvironment(
                        operations = operations,
                        workingDirectory = requestedWorkingDirectory,
                        environment = environment,
                    ) {
                        Unit
                    }
                }

                then("it fails with an illegal state error") {
                    execution.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
                }

                then("it reports the working-directory restoration failure") {
                    execution.exceptionOrNull()?.message.orEmpty()
                        .shouldContain("Failed to restore working directory to '/host/repo'")
                }

                then("it still restores the original environment values") {
                    operations.environment shouldContainExactly inheritedEnvironment
                }
            }
        }

        given("applying the environment overlay fails after a partial mutation") {
            val originalWorkingDirectory = "/host/repo"
            val inheritedEnvironment = mapOf("HOME" to "/host/home")
            val requestedWorkingDirectory = "/tmp/workspace"
            val environment = environmentOverlay {
                "HOME" setTo "/tmp/home"
                "XDG_DATA_HOME" setTo "/tmp/data"
            }
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = originalWorkingDirectory,
                    initialEnvironment = inheritedEnvironment,
                    failingSetKeys = setOf("XDG_DATA_HOME"),
                )

            action("the process context is applied") {
                val execution = runCatching {
                    withWorkingDirectoryAndEnvironment(
                        operations = operations,
                        workingDirectory = requestedWorkingDirectory,
                        environment = environment,
                    ) {
                        Unit
                    }
                }

                then("it fails with an illegal state error") {
                    execution.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
                }

                then("it reports the apply failure") {
                    execution.exceptionOrNull()?.message.orEmpty()
                        .shouldContain("Failed to set XDG_DATA_HOME to '/tmp/data'")
                }

                then("it still restores the original environment after the partial mutation") {
                    operations.environment shouldContainExactly inheritedEnvironment
                }

                then("it restores the original working directory") {
                    operations.workingDirectory shouldBe originalWorkingDirectory
                }
            }
        }

        given("reading captured process output fails") {
            val captureDirectory = "/tmp/loadout-e2e-capture"
            val requestedWorkingDirectory = "/tmp/workspace"
            val command = listOf("loadout", "status")
            val environment = environmentOverlay()
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = "/host/repo",
                    initialEnvironment = emptyMap(),
                ).apply {
                    createTemporaryDirectoryPath = captureDirectory
                    failingReadPath = "$captureDirectory/stdout.txt"
                    readFailure = IllegalStateException("capture read failed")
                }

            action("an external process result is collected") {
                val execution = runCatching {
                    runExternalProcess(
                        operations = operations,
                        workingDirectory = requestedWorkingDirectory,
                        command = command,
                        environment = environment,
                    )
                }

                then("it fails with an illegal state error") {
                    execution.exceptionOrNull().shouldBeInstanceOf<IllegalStateException>()
                }

                then("it surfaces the capture read failure") {
                    execution.exceptionOrNull()?.message shouldBe "capture read failed"
                }

                then("it still deletes the temporary capture directory") {
                    operations.deletedPaths.shouldContain(captureDirectory)
                }
            }
        }

        given("a Windows executable and its output capture paths contain spaces") {
            val command = listOf("C:/Program Files/Git/bin/git.exe", "status")
            val stdoutPath = "C:/Users/Test User/stdout.txt"
            val stderrPath = "C:/Users/Test User/stderr.txt"
            val shellDialect = ShellDialect.WindowsCmd

            action("a redirecting command line is built") {
                val commandLine =
                    buildRedirectingCommand(
                        command = command,
                        stdoutPath = stdoutPath,
                        stderrPath = stderrPath,
                        shellDialect = shellDialect,
                    )

                then("it uses cmd-compatible quoting for each argument and redirect target") {
                    commandLine shouldBe
                        "\"C:/Program Files/Git/bin/git.exe\" \"status\" > " +
                        "\"C:/Users/Test User/stdout.txt\" 2> " +
                        "\"C:/Users/Test User/stderr.txt\""
                }
            }
        }
    }
})

private data class ProcessContextSnapshot(
    val workingDirectory: String,
    val environment: Map<String, String>,
)

private class FakeNativeProcessOperations(
    initialWorkingDirectory: String,
    initialEnvironment: Map<String, String>,
    private val failingChangeDirectories: Set<String> = emptySet(),
    private val failingSetKeys: Set<String> = emptySet(),
) : NativeProcessOperations {
    var workingDirectory: String = initialWorkingDirectory
    val environment: MutableMap<String, String> = initialEnvironment.toMutableMap()
    val deletedPaths = mutableListOf<String>()
    var createTemporaryDirectoryPath: String = "/tmp/loadout-e2e-capture"
    var failingReadPath: String? = null
    var readFailure: Throwable? = null

    override fun currentWorkingDirectory(): String = workingDirectory

    override fun changeWorkingDirectory(path: String): Int {
        if (path in failingChangeDirectories) {
            return -1
        }

        workingDirectory = path
        return 0
    }

    override fun readEnvironmentVariable(key: String): String? = environment[key]

    override fun setEnvironmentVariable(
        key: String,
        value: String,
    ): Int {
        if (key in failingSetKeys) {
            return -1
        }

        environment[key] = value
        return 0
    }

    override fun clearEnvironmentVariable(key: String): Int {
        environment.remove(key)
        return 0
    }

    override fun executeShellCommand(command: String): Int = 0

    override fun createTemporaryDirectory(prefix: String): String = createTemporaryDirectoryPath

    override fun deleteRecursively(path: String) {
        deletedPaths += path
    }

    override fun readFileIfPresent(path: String): String {
        if (path == failingReadPath) {
            throw checkNotNull(readFailure)
        }

        return ""
    }
}
