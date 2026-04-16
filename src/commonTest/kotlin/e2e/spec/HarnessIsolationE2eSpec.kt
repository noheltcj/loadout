@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.givenGitRepositoryExists
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class HarnessIsolationE2eSpec : E2eBehaviorSuite({
    context("e2e harness isolation spec") {
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

private fun String.normalizeTempPath(): String = removePrefix("/private")
