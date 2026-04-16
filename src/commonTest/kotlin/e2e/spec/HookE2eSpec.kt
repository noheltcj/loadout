@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import cli.Constants
import e2e.support.E2eBehaviorSuite
import e2e.support.RepoSettings
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.architectFragmentPath
import e2e.support.givenGitRepositoryExists
import e2e.support.shouldContainInStderr
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedFiles
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class HookE2eSpec : E2eBehaviorSuite({
    context("loadout hook auto-sync spec") {
        given("a shared git repository has been initialized and committed") {
            val initializedSharedGitRepository: ScenarioSeed = {
                givenGitRepositoryExists()
                runCommand("init")
                writeWorkspaceFile("tracked.txt", "tracked\n")
                commitAllFiles("initial commit")
            }

            action("git worktree add is run for a new branch") {
                val execution by memoizedExecution(seed = initializedSharedGitRepository) {
                    runGit("worktree", "add", "-b", "prompt-sync", workspacePath("worktrees/prompt-sync"))
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it generates prompt files in the new worktree") {
                    val worktreeDirectory = execution.scenario.workspacePath("worktrees/prompt-sync")
                    execution.scenario.shouldHaveGeneratedFiles(directory = worktreeDirectory)
                }

                then("it records the resolved current loadout in the new worktree config") {
                    val worktreeDirectory = execution.scenario.workspacePath("worktrees/prompt-sync")
                    execution.scenario.readConfigFromDirectory(worktreeDirectory)?.currentLoadoutName shouldBe "default"
                }
            }

            given("another branch changes the active fragment content") {
                action("git switch is run to that branch") {
                    val updatedContent = "## Updated Architect\n\nBranch-specific hook sync guidance"
                    val execution by memoizedExecution(seed = initializedSharedGitRepository) {
                        runGit("switch", "-c", "updated")
                        writeWorkspaceFile(architectFragmentPath, updatedContent)
                        commitAllFiles("updated fragment")
                        runGit("switch", "-")
                        runGit("switch", "updated")
                    }

                    then("it refreshes the generated prompt files for the checked out branch") {
                        execution.scenario.readGeneratedBody(Constants.CLAUDE_MD) shouldBe updatedContent
                    }
                }
            }

            given("a feature branch changes the active fragment content") {
                action("git merge is run after that branch is merged") {
                    val mergedContent = "## Merged Architect\n\nMerged hook sync guidance"
                    val execution by memoizedExecution(seed = initializedSharedGitRepository) {
                        runGit("switch", "-c", "feature")
                        writeWorkspaceFile(architectFragmentPath, mergedContent)
                        commitAllFiles("feature fragment update")
                        runGit("switch", "-")
                        runGit("merge", "--no-edit", "feature")
                    }

                    then("it refreshes the generated prompt files after the merge") {
                        execution.scenario.readGeneratedBody(Constants.CLAUDE_MD) shouldBe mergedContent
                    }
                }
            }

            action("git checkout is run for a tracked file only") {
                val execution by memoizedExecution(seed = initializedSharedGitRepository) {
                    deleteWorkspaceFile(Constants.CLAUDE_MD)
                    runGit("checkout", "HEAD", "--", "tracked.txt")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it does not regenerate prompt files for a file checkout event") {
                    execution.scenario.readGeneratedFile(Constants.CLAUDE_MD) shouldBe null
                }
            }
        }

        given("a shared git repository with an invalid repo default loadout") {
            action("git switch is run") {
                val execution by memoizedExecution(
                    seed = {
                        givenGitRepositoryExists()
                        runCommand("init")
                        writeWorkspaceFile("tracked.txt", "tracked\n")
                        commitAllFiles("initial commit")
                        writeRepoSettings(RepoSettings(defaultLoadoutName = "missing"))
                    }
                ) {
                    runGit("switch", "-c", "invalid-default")
                }

                then("it preserves git success") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it reports one concise hook failure diagnostic") {
                    execution.result.shouldContainInStderr("missing")
                }
            }
        }

        given("a shared git repository whose hook helper cannot be executed") {
            action("git switch is run") {
                val execution by memoizedExecution(
                    seed = {
                        givenGitRepositoryExists()
                        runCommand("init")
                        writeWorkspaceFile("tracked.txt", "tracked\n")
                        commitAllFiles("initial commit")
                        runGit("config", "core.hooksPath", ".githooks")
                        writeWorkspaceFile(
                            ".githooks/post-checkout",
                            """
                            #!/bin/sh
                            /path/that/does/not/exist sync --auto >/dev/null 2>&1 || {
                              echo "Loadout hook failed: helper executable not found" >&2
                              exit 0
                            }
                            """.trimIndent()
                        )
                        setWorkspaceFileExecutable(".githooks/post-checkout")
                    }
                ) {
                    runGit("switch", "-c", "broken-helper")
                }

                then("it preserves git success") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it surfaces the helper execution failure") {
                    execution.result.stderr.shouldContain("Loadout hook failed: helper executable not found")
                }
            }
        }
    }
})
