@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.platform

import e2e.support.action
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class NativeProcessEnvironmentSafetySpec : BehaviorSpec({
    context("native process environment safety spec") {
        given("an inherited git environment exists") {
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = "/host/repo",
                    initialEnvironment =
                    mapOf(
                        "GIT_DIR" to "/host/repo/.git",
                        "GIT_WORK_TREE" to "/host/repo",
                    )
                )

            action("execution unsets the inherited git variables") {
                val observedEnvironment by lazy {
                    mutableMapOf<String, String?>().also { observed ->
                        withWorkingDirectoryAndEnvironment(
                            operations = operations,
                            workingDirectory = "/tmp/workspace",
                            environment =
                            environmentOverlay {
                                unset("GIT_DIR", "GIT_WORK_TREE")
                            },
                        ) {
                            observed["GIT_DIR"] = operations.readEnvironmentVariable("GIT_DIR")
                            observed["GIT_WORK_TREE"] = operations.readEnvironmentVariable("GIT_WORK_TREE")
                            observed["cwd"] = operations.currentWorkingDirectory()
                        }
                    }
                }

                then("it clears the git variables during execution") {
                    observedEnvironment["GIT_DIR"] shouldBe null
                    observedEnvironment["GIT_WORK_TREE"] shouldBe null
                }

                then("it runs the block in the requested working directory") {
                    observedEnvironment["cwd"] shouldBe "/tmp/workspace"
                }

                then("it restores the inherited git environment afterward") {
                    operations.environment shouldContainExactly
                        mapOf(
                            "GIT_DIR" to "/host/repo/.git",
                            "GIT_WORK_TREE" to "/host/repo",
                        )
                }
            }
        }

        given("restoring the original working directory fails") {
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = "/host/repo",
                    initialEnvironment =
                    mapOf(
                        "HOME" to "/host/home",
                        "XDG_DATA_HOME" to "/host/data",
                    ),
                    failingChangeDirectories = setOf("/host/repo")
                )

            action("the process context is cleaned up") {
                val failure by lazy {
                    shouldThrow<IllegalStateException> {
                        withWorkingDirectoryAndEnvironment(
                            operations = operations,
                            workingDirectory = "/tmp/workspace",
                            environment =
                            environmentOverlay {
                                "HOME" setTo "/tmp/home"
                                "XDG_DATA_HOME" setTo "/tmp/data"
                            },
                        ) {
                            Unit
                        }
                    }
                }

                then("it reports the working-directory restoration failure") {
                    failure.message.shouldContain("Failed to restore working directory to '/host/repo'")
                }

                then("it still restores the original environment values") {
                    operations.environment shouldContainExactly
                        mapOf(
                            "HOME" to "/host/home",
                            "XDG_DATA_HOME" to "/host/data",
                        )
                }
            }
        }

        given("applying the environment overlay fails after a partial mutation") {
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = "/host/repo",
                    initialEnvironment =
                    mapOf(
                        "HOME" to "/host/home",
                    ),
                    failingSetKeys = setOf("XDG_DATA_HOME")
                )

            action("the overlay apply fails") {
                val failure by lazy {
                    shouldThrow<IllegalStateException> {
                        withWorkingDirectoryAndEnvironment(
                            operations = operations,
                            workingDirectory = "/tmp/workspace",
                            environment =
                            environmentOverlay {
                                "HOME" setTo "/tmp/home"
                                "XDG_DATA_HOME" setTo "/tmp/data"
                            },
                        ) {
                            Unit
                        }
                    }
                }

                then("it reports the apply failure") {
                    failure.message.shouldContain("Failed to set XDG_DATA_HOME to '/tmp/data'")
                }

                then("it still restores the original environment after the partial mutation") {
                    operations.environment shouldContainExactly mapOf("HOME" to "/host/home")
                }

                then("it restores the original working directory") {
                    operations.workingDirectory shouldBe "/host/repo"
                }
            }
        }

        given("reading captured process output fails") {
            val operations =
                FakeNativeProcessOperations(
                    initialWorkingDirectory = "/host/repo",
                    initialEnvironment = emptyMap(),
                ).apply {
                    createTemporaryDirectoryPath = "/tmp/loadout-e2e-capture"
                    failingReadPath = "/tmp/loadout-e2e-capture/stdout.txt"
                    readFailure = IllegalStateException("capture read failed")
                }

            action("an external process result is collected") {
                val failure by lazy {
                    shouldThrow<IllegalStateException> {
                        runExternalProcess(
                            operations = operations,
                            workingDirectory = "/tmp/workspace",
                            command = listOf("loadout", "status"),
                            environment = environmentOverlay(),
                        )
                    }
                }

                then("it surfaces the capture read failure") {
                    failure.message shouldBe "capture read failed"
                }

                then("it still deletes the temporary capture directory") {
                    operations.deletedPaths.shouldContain("/tmp/loadout-e2e-capture")
                }
            }
        }

        given("building a Windows command line for an external process") {
            action("the executable and capture paths contain spaces") {
                val commandLine by lazy {
                    buildRedirectingCommand(
                        command = listOf("C:/Program Files/Git/bin/git.exe", "status"),
                        stdoutPath = "C:/Users/Test User/stdout.txt",
                        stderrPath = "C:/Users/Test User/stderr.txt",
                        shellDialect = ShellDialect.WindowsCmd,
                    )
                }

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

    override fun setEnvironmentVariable(key: String, value: String): Int {
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
