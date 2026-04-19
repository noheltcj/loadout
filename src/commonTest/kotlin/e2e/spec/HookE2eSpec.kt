@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import cli.Constants
import e2e.support.E2eBehaviorSuite
import e2e.support.RepoSettings
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
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
                    // TODO: This should use the scenario helper functions. Those may require modification to support this.
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
                val updatedContent = "## Updated Architect\n\nBranch-specific hook sync guidance"
                val updatedBranchChangesActiveFragmentContent: ScenarioSeed =
                    initializedSharedGitRepository.andThen {
                        runGit("switch", "-c", "updated").shouldHaveExitCode(0)
                        writeWorkspaceFile(architectFragmentPath, updatedContent)
                        commitAllFiles("updated fragment")
                        runGit("switch", "-").shouldHaveExitCode(0)
                    }

                action("git switch is run to that branch") {
                    val execution by memoizedExecution(seed = updatedBranchChangesActiveFragmentContent) {
                        runGit("switch", "updated")
                    }

                    then("it refreshes the generated prompt files for the checked out branch") {
                        execution.scenario.readGeneratedBody(Constants.CLAUDE_MD) shouldBe updatedContent
                    }
                }
            }

            given("a feature branch changes the active fragment content") {
                val mergedContent = "## Merged Architect\n\nMerged hook sync guidance"
                val featureBranchChangesActiveFragmentContent: ScenarioSeed =
                    initializedSharedGitRepository.andThen {
                        runGit("switch", "-c", "feature").shouldHaveExitCode(0)
                        writeWorkspaceFile(architectFragmentPath, mergedContent)
                        commitAllFiles("feature fragment update")
                        runGit("switch", "-").shouldHaveExitCode(0)
                    }

                action("git merge is run after that branch is merged") {
                    val execution by memoizedExecution(seed = featureBranchChangesActiveFragmentContent) {
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
            val sharedGitRepositoryWithInvalidRepoDefaultLoadout: ScenarioSeed =
                initializedSharedGitRepositorySeed().andThen {
                    writeRepoSettings(RepoSettings(defaultLoadoutName = "missing"))
                }

            action("git switch is run") {
                val execution by memoizedExecution(
                    seed = sharedGitRepositoryWithInvalidRepoDefaultLoadout
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
            val sharedGitRepositoryWithBrokenHookHelper: ScenarioSeed =
                initializedSharedGitRepositorySeed().andThen {
                    runGit("config", "core.hooksPath", ".githooks").shouldHaveExitCode(0)
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

            action("git switch is run") {
                val execution by memoizedExecution(
                    seed = sharedGitRepositoryWithBrokenHookHelper
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

private fun initializedSharedGitRepositorySeed(): ScenarioSeed =
    {
        givenGitRepositoryExists()
        runCommand("init")
        writeWorkspaceFile("tracked.txt", "tracked\n")
        commitAllFiles("initial commit")
    }
