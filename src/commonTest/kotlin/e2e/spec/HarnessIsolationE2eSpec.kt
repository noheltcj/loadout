@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.platform.EnvironmentOverlay
import e2e.platform.currentWorkingDirectory
import e2e.platform.environmentOverlay
import e2e.platform.readEnvironmentVariable
import e2e.platform.withWorkingDirectoryAndEnvironment
import e2e.support.CommandResult
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.givenGitRepositoryExists
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class HarnessIsolationE2eSpec : E2eBehaviorSuite({
    context("e2e harness isolation spec ($harnessSafetyReviewPath)") {
        given("an isolated temp workspace") {
            action("the dedicated loadout helper executable is invoked directly") {
                val execution by memoizedExecution {
                    runExternalCommand(loadoutHelperExecutablePath(), "--help")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it behaves like the loadout cli entrypoint") {
                    execution.result.shouldContainInStdout("Composable, shareable .md system prompts")
                }
            }

            action("an external process inherits conflicting host XDG variables") {
                val execution by memoizedExecution {
                    val poisonedEnvironment =
                        environmentOverlay {
                            "HOME" setTo "/host/home"
                            "XDG_CONFIG_HOME" setTo "/host/config"
                            "XDG_DATA_HOME" setTo "/host/data"
                            "XDG_STATE_HOME" setTo "/host/state"
                            "XDG_CACHE_HOME" setTo "/host/cache"
                        }
                    val visibleEnvironment =
                        inspectExternalEnvironment(
                            "HOME",
                            "XDG_CONFIG_HOME",
                            "XDG_DATA_HOME",
                            "XDG_STATE_HOME",
                            "XDG_CACHE_HOME",
                            environment = poisonedEnvironment
                        )

                    CommandResult(
                        stdout = visibleEnvironment.entries.joinToString("\n") { (key, value) -> "$key=$value" },
                        stderr = "",
                        output = visibleEnvironment.entries.joinToString("\n") { (key, value) -> "$key=$value" },
                        exitCode = 0,
                    )
                }

                then("it exposes only temp-rooted XDG directories to the child process") {
                    val expectedEnvironment =
                        mapOf(
                            "HOME" to execution.scenario.homeRoot,
                            "XDG_CONFIG_HOME" to execution.scenario.xdgConfigRoot,
                            "XDG_DATA_HOME" to execution.scenario.homePath(".local/share"),
                            "XDG_STATE_HOME" to execution.scenario.homePath(".local/state"),
                            "XDG_CACHE_HOME" to execution.scenario.homePath(".cache"),
                        )
                    execution.result.stdout
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .associate { line ->
                            val delimiterIndex = line.indexOf('=')
                            line.substring(0, delimiterIndex) to line.substring(delimiterIndex + 1)
                        } shouldContainExactly expectedEnvironment
                }
            }

            action("an external process inherits a PATH with relative and workspace-local entries") {
                val execution by memoizedExecution {
                    val safePath = homePath("tool-bin")
                    val pathSeparator = hostPathListSeparator()
                    val poisonedPath =
                        listOf(
                            "./bin",
                            workspacePath("tool-bin"),
                            "${currentWorkingDirectory()}/tool-bin",
                            safePath,
                        ).joinToString(pathSeparator)

                    val visibleEnvironment =
                        withWorkingDirectoryAndEnvironment(
                            workingDirectory = workspaceRoot,
                            environment =
                                environmentOverlay {
                                    "PATH" setTo poisonedPath
                                },
                        ) {
                            inspectExternalEnvironment("PATH")
                        }

                    CommandResult(
                        stdout = visibleEnvironment.entries.joinToString("\n") { (key, value) -> "$key=$value" },
                        stderr = "",
                        output = visibleEnvironment.entries.joinToString("\n") { (key, value) -> "$key=$value" },
                        exitCode = 0,
                    )
                }

                then("it removes relative and workspace-local PATH entries before launching the child process") {
                    execution.result.stdout.trim() shouldBe "PATH=${execution.scenario.homePath("tool-bin")}"
                }
            }

            action("an external process inherits only unsafe PATH entries") {
                val scenario by memoizedScenario()
                val failure by lazy {
                    val pathSeparator = hostPathListSeparator()
                    val poisonedPath =
                        listOf(
                            "./bin",
                            scenario.workspacePath("tool-bin"),
                            "${currentWorkingDirectory()}/tool-bin",
                        ).joinToString(pathSeparator)

                    shouldThrow<IllegalStateException> {
                        withWorkingDirectoryAndEnvironment(
                            workingDirectory = scenario.workspaceRoot,
                            environment =
                                environmentOverlay {
                                    "PATH" setTo poisonedPath
                                },
                        ) {
                            scenario.inspectExternalEnvironment("PATH")
                        }
                    }
                }

                then("it fails fast instead of inheriting the host PATH") {
                    failure.message.shouldContain("PATH sanitization removed all safe entries")
                }
            }
        }

        given("an isolated git repository exists") {
            val isolatedGitRepository: ScenarioSeed = {
                givenGitRepositoryExists()
            }

            action("git repository discovery is run from a nested directory") {
                val execution by memoizedExecution(seed = isolatedGitRepository) {
                    writeWorkspaceFile("nested/.keep", "keep\n")
                    runGit("rev-parse", "--show-toplevel", workingDirectory = workspacePath("nested"))
                }

                then("it resolves the temp repo as the top-level directory") {
                    execution.result.stdout.trim().normalizeTempPath() shouldBe
                        execution.scenario.workspaceRoot.normalizeTempPath()
                }
            }

            action("git repository discovery is run with inherited git directory variables") {
                val execution by memoizedExecution(seed = isolatedGitRepository) {
                    writeWorkspaceFile("nested/.keep", "keep\n")
                    writeWorkspaceFile("foreign-repo/.keep", "keep\n")
                    initializeGitRepository(workingDirectory = workspacePath("foreign-repo"))
                    runGit(
                        "rev-parse",
                        "--show-toplevel",
                        workingDirectory = workspacePath("nested"),
                        environment =
                            environmentOverlay {
                                "GIT_DIR" setTo workspacePath("foreign-repo/.git")
                                "GIT_WORK_TREE" setTo workspacePath("foreign-repo")
                            }
                    )
                }

                then("it ignores the inherited git directory variables and stays inside the temp repo") {
                    execution.result.stdout.trim().normalizeTempPath() shouldBe
                        execution.scenario.workspaceRoot.normalizeTempPath()
                }
            }

            action("git worktree metadata is inspected from a temp worktree") {
                val execution by memoizedExecution(seed = isolatedGitRepository) {
                    writeWorkspaceFile("README.md", "temp repo\n")
                    commitAllFiles("initial commit")
                    runGit("worktree", "add", "-b", "feature", workspacePath("worktrees/feature"))
                        .shouldHaveExitCode(0)
                    runGit("rev-parse", "--git-common-dir", workingDirectory = workspacePath("worktrees/feature"))
                }

                then("it resolves the git common dir inside the temp repository") {
                    execution.result.stdout.trim().normalizeTempPath() shouldStartWith
                        execution.scenario.workspaceRoot.normalizeTempPath()
                }
            }
        }
    }
})

private const val harnessSafetyReviewPath = "reviews/current-review-issues.md"

private fun hostPathListSeparator(): String = if ((readEnvironmentVariable("PATH") ?: "").contains(';')) ";" else ":"

private fun String.normalizeTempPath(): String = removePrefix("/private")
