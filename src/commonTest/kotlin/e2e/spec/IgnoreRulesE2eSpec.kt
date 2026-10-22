@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import cli.Constants
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.defaultOutputFilePaths
import e2e.support.gitignorePath
import e2e.support.givenFragmentsHomeIsConfigured
import e2e.support.givenGitRepositoryInitializedInLocalMode
import e2e.support.givenInitializedGitRepository
import e2e.support.givenLinkedWorktreeWasAdded
import e2e.support.givenLinkedWorktreeWasInitializedAndThenRemoved
import e2e.support.givenUninitializedGitRepository
import e2e.support.hooksDirectoryPath
import e2e.support.linkedWorktreeName
import e2e.support.linkedWorktreePath
import e2e.support.nestedAgentFilePath
import e2e.support.requireWorkspaceFile
import e2e.support.shouldBeHiddenFromGitStatus
import e2e.support.shouldBeVisibleToGitStatus
import e2e.support.shouldContainInStderr
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldHaveGitLocalConfig
import e2e.support.shouldHaveNoUntrackedFilesInGitStatus
import e2e.support.shouldHaveWorktreeExcludeSection
import e2e.support.shouldHideDirectoryFromGitStatus
import e2e.support.shouldNotHaveWorktreeExcludeSection
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val GIT_EXCLUDE_PATH = ".git/info/exclude"
private const val IGNORE_RULES_WARNING = "Warning: Could not update git ignore rules"
private const val LINKED_WORKTREE_EXCLUDE_KEY = "worktrees/$linkedWorktreeName"
private const val MAIN_WORKTREE_EXCLUDE_KEY = "main"

class IgnoreRulesE2eSpec : E2eBehaviorSuite({
    context("ignore rules spec") {
        given("an uninitialized git repository") {
            val uninitializedGitRepository: ScenarioSeed = {
                givenUninitializedGitRepository()
            }

            action("loadout init is run") {
                val execution by memoizedCapturedExecution(
                    seed = uninitializedGitRepository,
                    capture = { requireWorkspaceFile(gitignorePath) },
                ) { _ ->
                    runCommand("init")
                }

                then("it leaves .gitignore unchanged") {
                    execution.scenario.requireWorkspaceFile(gitignorePath) shouldBe execution.captured
                }

                then("it leaves no generated output untracked in git status") {
                    execution.scenario.shouldBeHiddenFromGitStatus(*defaultOutputFilePaths.toTypedArray())
                }

                then("it keeps the local runtime state out of git status") {
                    execution.scenario.shouldBeHiddenFromGitStatus(Constants.LOCAL_LOADOUT_STATE_FILE)
                }

                then("it leaves the loadout definitions visible to git status") {
                    execution.scenario.shouldBeVisibleToGitStatus("${Constants.LOADOUTS_DIR}/default.json")
                }

                then("it leaves the repository settings visible to git status") {
                    execution.scenario.shouldBeVisibleToGitStatus(Constants.REPOSITORY_SETTINGS_FILE)
                }

                then("it reports that it configured git to ignore Loadout files in .git/info/exclude") {
                    execution.result.shouldContainInStdout(
                        "Configured git to ignore Loadout files in $GIT_EXCLUDE_PATH (Shared mode)"
                    )
                }
            }

            action("loadout init is run with --mode local") {
                val execution by memoizedCapturedExecution(
                    seed = uninitializedGitRepository,
                    capture = { requireWorkspaceFile(gitignorePath) },
                ) { _ ->
                    runCommand("init", "--mode", "local")
                }

                then("it leaves .gitignore unchanged") {
                    execution.scenario.requireWorkspaceFile(gitignorePath) shouldBe execution.captured
                }

                then("it leaves no Loadout file untracked in git status") {
                    execution.scenario.shouldHaveNoUntrackedFilesInGitStatus()
                }
            }

            given("a hand-written packages/api/CLAUDE.md exists") {
                val handWrittenNestedAgentFile: ScenarioSeed =
                    uninitializedGitRepository.andThen {
                        writeWorkspaceFile(nestedAgentFilePath, "# API guidance\n")
                    }

                action("loadout init is run") {
                    val execution by memoizedAction("init", seed = handWrittenNestedAgentFile)

                    then("it leaves packages/api/CLAUDE.md visible to git status") {
                        execution.scenario.shouldBeVisibleToGitStatus(nestedAgentFilePath)
                    }
                }
            }

            given("a custom fragments home is configured") {
                val customFragmentsHome: ScenarioSeed =
                    uninitializedGitRepository.andThen {
                        givenFragmentsHomeIsConfigured("guidance/library")
                        seedFragment("guidance/library/notes.md", "## Notes\n\nLocal notes")
                        seedFragment("fragments/shared.md", "## Shared\n\nShared notes")
                    }

                action("loadout init is run with --mode local") {
                    val execution by memoizedAction("init", "--mode", "local", seed = customFragmentsHome)

                    then("it leaves no file in the configured fragments home untracked in git status") {
                        execution.scenario.shouldHideDirectoryFromGitStatus("guidance/library")
                    }

                    then("it leaves the default fragments directory visible to git status") {
                        execution.scenario.shouldBeVisibleToGitStatus("fragments/shared.md")
                    }
                }
            }

            given("its exclude file has an unterminated Loadout section") {
                val unterminatedExcludeSection: ScenarioSeed =
                    uninitializedGitRepository.andThen {
                        writeWorkspaceFile(
                            GIT_EXCLUDE_PATH,
                            "# >>> loadout (worktree: main) - managed by Loadout, do not edit\n/CLAUDE.md\n*.local\n",
                        )
                    }

                action("loadout init is run") {
                    val execution by memoizedCapturedExecution(
                        seed = unterminatedExcludeSection,
                        capture = { requireWorkspaceFile(GIT_EXCLUDE_PATH) },
                    ) { _ ->
                        runCommand("init")
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }

                    then("it warns that the git ignore rules could not be updated") {
                        execution.result.shouldContainInStderr(IGNORE_RULES_WARNING)
                    }

                    then("it leaves the exclude file unchanged") {
                        execution.scenario.requireWorkspaceFile(GIT_EXCLUDE_PATH) shouldBe execution.captured
                    }
                }
            }

            given("a linked worktree was added") {
                val linkedWorktreeWasAdded: ScenarioSeed =
                    uninitializedGitRepository.andThen {
                        givenLinkedWorktreeWasAdded()
                    }

                action("loadout init is run in the linked worktree") {
                    val execution by memoizedExecution(seed = linkedWorktreeWasAdded) {
                        runCommand("init", workingDirectory = linkedWorktreePath())
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }

                    then("it sets a hooks path that git reads in the linked worktree") {
                        execution.scenario.shouldHaveGitLocalConfig(
                            "core.hooksPath",
                            hooksDirectoryPath,
                            workingDirectory = execution.scenario.linkedWorktreePath(),
                        )
                    }

                    then("it adds an exclude section for the linked worktree") {
                        execution.scenario.shouldHaveWorktreeExcludeSection(LINKED_WORKTREE_EXCLUDE_KEY)
                    }

                    then("it leaves no generated output untracked in the linked worktree's git status") {
                        execution.scenario.shouldBeHiddenFromGitStatus(
                            *defaultOutputFilePaths.toTypedArray(),
                            workingDirectory = execution.scenario.linkedWorktreePath(),
                        )
                    }
                }
            }
        }

        given("an initialized git repository") {
            val initializedGitRepository: ScenarioSeed = {
                givenInitializedGitRepository()
            }

            action("loadout init is run again") {
                val execution by memoizedCapturedExecution(
                    seed = initializedGitRepository,
                    capture = { requireWorkspaceFile(GIT_EXCLUDE_PATH) },
                ) { _ ->
                    runCommand("init")
                }

                then("it reports that git already ignores Loadout files") {
                    execution.result.shouldContainInStdout(
                        "Git already ignores Loadout files in $GIT_EXCLUDE_PATH (Shared mode)"
                    )
                }

                then("it leaves the exclude file unchanged") {
                    execution.scenario.requireWorkspaceFile(GIT_EXCLUDE_PATH) shouldBe execution.captured
                }
            }

            action("git worktree add is run") {
                val execution by memoizedExecution(seed = initializedGitRepository) {
                    runGit("worktree", "add", "--detach", linkedWorktreePath())
                }

                then("it adds an exclude section for the new worktree") {
                    execution.scenario.shouldHaveWorktreeExcludeSection(LINKED_WORKTREE_EXCLUDE_KEY)
                }

                then("it keeps the main worktree's exclude section") {
                    execution.scenario.shouldHaveWorktreeExcludeSection(MAIN_WORKTREE_EXCLUDE_KEY)
                }
            }

            action("git worktree add is run for a directory named main") {
                val execution by memoizedExecution(seed = initializedGitRepository) {
                    runGit("worktree", "add", "--detach", linkedWorktreePath("main"))
                }

                then("it adds a separate exclude section for the new worktree") {
                    execution.scenario.shouldHaveWorktreeExcludeSection("worktrees/main")
                }

                then("it keeps the main worktree's exclude section") {
                    execution.scenario.shouldHaveWorktreeExcludeSection(MAIN_WORKTREE_EXCLUDE_KEY)
                }
            }

            given("a linked worktree was added") {
                val linkedWorktreeWasAdded: ScenarioSeed =
                    initializedGitRepository.andThen {
                        givenLinkedWorktreeWasAdded()
                    }

                action("loadout init is run in the linked worktree") {
                    val execution by memoizedExecution(seed = linkedWorktreeWasAdded) {
                        runCommand("init", workingDirectory = linkedWorktreePath())
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }

                    then("it keeps the main worktree's exclude section") {
                        execution.scenario.shouldHaveWorktreeExcludeSection(MAIN_WORKTREE_EXCLUDE_KEY)
                    }
                }

                given("the linked worktree was initialized and then removed") {
                    val linkedWorktreeWasInitializedAndThenRemoved: ScenarioSeed =
                        linkedWorktreeWasAdded.andThen {
                            givenLinkedWorktreeWasInitializedAndThenRemoved()
                        }

                    action("loadout sync is run in the main worktree") {
                        val execution by memoizedAction("sync", seed = linkedWorktreeWasInitializedAndThenRemoved)

                        then("it drops the removed worktree's exclude section") {
                            execution.scenario.shouldNotHaveWorktreeExcludeSection(LINKED_WORKTREE_EXCLUDE_KEY)
                        }

                        then("it keeps the main worktree's exclude section") {
                            execution.scenario.shouldHaveWorktreeExcludeSection(MAIN_WORKTREE_EXCLUDE_KEY)
                        }
                    }
                }
            }

            given("git's exclude file cannot be written") {
                val excludeFileCannotBeWritten: ScenarioSeed =
                    initializedGitRepository.andThen {
                        runExternalCommand("rm", "-rf", workspacePath(".git/info")).shouldHaveExitCode(0)
                        writeWorkspaceFile(".git/info", "not a directory\n")
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = excludeFileCannotBeWritten)

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }

                    then("it warns that the git ignore rules could not be updated") {
                        execution.result.shouldContainInStderr(IGNORE_RULES_WARNING)
                    }
                }
            }
        }

        given("a git repository initialized in local mode") {
            val gitRepositoryInitializedInLocalMode: ScenarioSeed = {
                givenGitRepositoryInitializedInLocalMode()
            }

            action("loadout sync is run") {
                val execution by memoizedAction("sync", seed = gitRepositoryInitializedInLocalMode)

                then("it still leaves no Loadout file untracked in git status") {
                    execution.scenario.shouldHaveNoUntrackedFilesInGitStatus()
                }
            }

            action("loadout init is run in shared mode") {
                val execution by memoizedAction("init", seed = gitRepositoryInitializedInLocalMode)

                then("it leaves the loadout definitions visible to git status") {
                    execution.scenario.shouldBeVisibleToGitStatus("${Constants.LOADOUTS_DIR}/default.json")
                }
            }

            given("the fragments home was then changed") {
                val fragmentsHomeWasChanged: ScenarioSeed =
                    gitRepositoryInitializedInLocalMode.andThen {
                        givenFragmentsHomeIsConfigured("guidance")
                        seedFragment("guidance/notes.md", "## Notes\n\nLocal notes")
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = fragmentsHomeWasChanged)

                    then("it leaves no file in the new fragments home untracked in git status") {
                        execution.scenario.shouldHideDirectoryFromGitStatus("guidance")
                    }
                }
            }
        }

        given("a workspace that is not a git repository") {
            action("loadout init is run") {
                val execution by memoizedAction("init")

                then("it writes the generated outputs") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }

                then("it does not create a .gitignore") {
                    execution.scenario.readWorkspaceFile(gitignorePath).shouldBeNull()
                }

                then("it reports that git ignore rules were skipped") {
                    execution.result.shouldContainInStdout("Skipped git ignore rules: not a git repository")
                }
            }
        }
    }
})
