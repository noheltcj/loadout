@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import domain.policy.GENERATED_OUTPUT_HEADER
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.agentsFilePath
import e2e.support.andThen
import e2e.support.claudeFilePath
import e2e.support.copilotInstructionsFilePath
import e2e.support.geminiFilePath
import e2e.support.givenCurrentLoadoutWasSynced
import e2e.support.givenHandWrittenAgentFileExists
import e2e.support.givenInitializedGitRepository
import e2e.support.givenOutputFileWasEdited
import e2e.support.givenRepositoryOutputFilesAreSet
import e2e.support.givenRepositorySettingsListOutputFiles
import e2e.support.handWrittenAgentGuidance
import e2e.support.requireBackupContent
import e2e.support.requireBackupPath
import e2e.support.requireRepositorySettings
import e2e.support.requireWorkspaceFile
import e2e.support.shouldBeHiddenFromGitStatus
import e2e.support.shouldBeVisibleToGitStatus
import e2e.support.shouldContainInStderr
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldListOnlyInWorktreeExcludeSection
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

// Init creates an empty default loadout, so its composed body is empty.
private const val DEFAULT_LOADOUT_BODY = ""
private const val MAIN_WORKTREE_EXCLUDE_KEY = "main"
private const val OUTPUT_FILES_VALIDATION_ERROR = "Validation error in 'output-files'"
private const val NESTED_CLAUDE_FILE_PATH = "docs/CLAUDE.md"
private const val INVALID_OUTSIDE_OUTPUT_FILE_WARNING = "Warning: Invalid output file '../outside.md' was removed"

class RepositoryOutputFilesE2eSpec : E2eBehaviorSuite({
    context("repository output files spec") {
        given("an initialized repository") {
            val initializedRepository: ScenarioSeed = {
                givenInitializedGitRepository()
            }

            action("loadout sync is run") {
                val execution by memoizedAction("sync", seed = initializedRepository)

                then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }
            }

            action("loadout config is run with --output-files CLAUDE.md,AGENTS.md") {
                val execution by memoizedAction(
                    "config",
                    "--output-files",
                    "CLAUDE.md,AGENTS.md",
                    seed = initializedRepository,
                )

                then("it saves the list to the repository settings") {
                    execution.scenario.requireRepositorySettings().outputFiles.shouldNotBeNull()
                        .shouldContainExactly(claudeFilePath, agentsFilePath)
                }

                then("it reports CLAUDE.md, AGENTS.md as the effective output files from the repository setting") {
                    execution.result.shouldContainInStdout("Output files: CLAUDE.md, AGENTS.md (repository setting)")
                }

                then("it tells the user to run loadout sync") {
                    execution.result.shouldContainInStderr("Run 'loadout sync' to apply.")
                }
            }

            action("loadout config is run with --output-files .github/copilot-instructions.md") {
                val execution by memoizedAction(
                    "config",
                    "--output-files",
                    copilotInstructionsFilePath,
                    seed = initializedRepository,
                )

                then("it saves the list to the repository settings") {
                    execution.scenario.requireRepositorySettings().outputFiles.shouldNotBeNull()
                        .shouldContainExactly(copilotInstructionsFilePath)
                }
            }

            action("loadout config is run with --output-files ../outside.md") {
                val execution by memoizedCapturedExecution(
                    seed = initializedRepository,
                    capture = { requireRepositorySettings() },
                ) { _ ->
                    runCommand("config", "--output-files", "../outside.md")
                }

                then("it outputs a validation error naming ../outside.md") {
                    execution.result.shouldContainInStderr("$OUTPUT_FILES_VALIDATION_ERROR: '../outside.md'")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it leaves the repository settings unchanged") {
                    execution.scenario.requireRepositorySettings() shouldBe execution.captured
                }
            }

            action("loadout config is run with --output-files .git/notes.md") {
                val execution by memoizedAction(
                    "config",
                    "--output-files",
                    ".git/notes.md",
                    seed = initializedRepository,
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInStderr(
                        "$OUTPUT_FILES_VALIDATION_ERROR: '.git/notes.md' is inside .git/"
                    )
                }
            }

            action("loadout config is run with --output-files fragments/notes.md") {
                val execution by memoizedAction(
                    "config",
                    "--output-files",
                    "fragments/notes.md",
                    seed = initializedRepository,
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInStderr(
                        "$OUTPUT_FILES_VALIDATION_ERROR: 'fragments/notes.md' is inside fragments/"
                    )
                }
            }

            action("loadout config is run with --output-files CLAUDE.md,claude.md") {
                val execution by memoizedAction(
                    "config",
                    "--output-files",
                    "CLAUDE.md,claude.md",
                    seed = initializedRepository,
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInStderr(
                        "$OUTPUT_FILES_VALIDATION_ERROR: 'claude.md' and 'CLAUDE.md' differ only by case"
                    )
                }
            }

            action("loadout config is run with an empty --output-files value") {
                val execution by memoizedAction("config", "--output-files", "", seed = initializedRepository)

                then("it outputs a validation error that suggests --unset-output-files") {
                    execution.result.shouldContainInStderr(
                        "List at least one output file, or remove the repository setting with " +
                            "'loadout config --unset-output-files'."
                    )
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            given("the repository output files are set to CLAUDE.md") {
                val outputFilesAreClaudeOnly: ScenarioSeed =
                    initializedRepository.andThen {
                        givenRepositoryOutputFilesAreSet(claudeFilePath)
                    }

                action("loadout is run without a subcommand") {
                    val execution by memoizedAction(seed = outputFilesAreClaudeOnly)

                    then("it warns that the output files changed since the last sync") {
                        execution.result.shouldContainInStderr(
                            "Warning: Your output files changed since the last sync (removes AGENTS.md, GEMINI.md)."
                        )
                    }
                }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = outputFilesAreClaudeOnly)

                    then("it deletes AGENTS.md") {
                        execution.scenario.readWorkspaceFile(agentsFilePath).shouldBeNull()
                    }

                    then("it deletes GEMINI.md") {
                        execution.scenario.readWorkspaceFile(geminiFilePath).shouldBeNull()
                    }

                    then("it reports the removed files") {
                        execution.result.shouldContainInStdout(
                            "Removed output files that are no longer configured:\n  • AGENTS.md\n  • GEMINI.md\n"
                        )
                    }

                    then("it lists only /CLAUDE.md in this worktree's exclude section") {
                        execution.scenario.shouldListOnlyInWorktreeExcludeSection(
                            MAIN_WORKTREE_EXCLUDE_KEY,
                            "/CLAUDE.md",
                        )
                    }
                }

                action("loadout config is run with --unset-output-files") {
                    val execution by memoizedAction("config", "--unset-output-files", seed = outputFilesAreClaudeOnly)

                    then("it removes the list from the repository settings") {
                        execution.scenario.requireRepositorySettings().outputFiles.shouldBeNull()
                    }

                    then("it reports the built-in default as the effective output files") {
                        execution.result.shouldContainInStdout(
                            "Output files: CLAUDE.md, AGENTS.md, GEMINI.md (built-in default)"
                        )
                    }
                }

                given("AGENTS.md was edited after Loadout generated it") {
                    val agentsFileWasEdited: ScenarioSeed =
                        outputFilesAreClaudeOnly.andThen {
                            givenOutputFileWasEdited(agentsFilePath)
                        }

                    action("loadout sync is run") {
                        val execution by memoizedCapturedExecution(
                            seed = agentsFileWasEdited,
                            capture = { requireWorkspaceFile(agentsFilePath) },
                        ) { _ ->
                            runCommand("sync")
                        }

                        then("it keeps AGENTS.md") {
                            execution.scenario.readWorkspaceFile(agentsFilePath) shouldBe execution.captured
                        }

                        then("it warns that Loadout no longer manages AGENTS.md") {
                            execution.result.shouldContainInStderr(
                                "Warning: Kept AGENTS.md because it changed after Loadout generated it."
                            )
                        }

                        then("it leaves AGENTS.md visible to git status") {
                            execution.scenario.shouldBeVisibleToGitStatus(agentsFilePath)
                        }
                    }
                }

                given("the current loadout was then synced") {
                    val outputFilesWereSynced: ScenarioSeed =
                        outputFilesAreClaudeOnly.andThen {
                            givenCurrentLoadoutWasSynced()
                        }

                    action("loadout sync is run") {
                        val execution by memoizedAction("sync", seed = outputFilesWereSynced)

                        then("it outputs that the current loadout is active and up to date") {
                            execution.result.shouldContainInStdout(
                                "Loadout `default` is active and up to date. Nothing to do."
                            )
                        }
                    }
                }
            }

            given("the repository output files are set to CLAUDE.md and .github/copilot-instructions.md") {
                val outputFilesIncludeCopilotInstructions: ScenarioSeed =
                    initializedRepository.andThen {
                        givenRepositoryOutputFilesAreSet(claudeFilePath, copilotInstructionsFilePath)
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = outputFilesIncludeCopilotInstructions)

                    then("it writes .github/copilot-instructions.md in a new .github directory") {
                        execution.scenario.requireWorkspaceFile(copilotInstructionsFilePath) shouldStartWith
                            GENERATED_OUTPUT_HEADER
                    }

                    then("it leaves .github/copilot-instructions.md out of git status") {
                        execution.scenario.shouldBeHiddenFromGitStatus(copilotInstructionsFilePath)
                    }
                }

                given("a hand-written .github/copilot-instructions.md exists") {
                    val handWrittenCopilotInstructions: ScenarioSeed =
                        outputFilesIncludeCopilotInstructions.andThen {
                            givenHandWrittenAgentFileExists(copilotInstructionsFilePath)
                        }

                    action("loadout sync is run") {
                        val execution by memoizedAction("sync", seed = handWrittenCopilotInstructions)

                        then("it saves the hand-written content to a backup") {
                            execution.scenario.requireBackupContent(copilotInstructionsFilePath) shouldBe
                                handWrittenAgentGuidance
                        }

                        then("it writes the composed body to .github/copilot-instructions.md") {
                            execution.scenario.shouldHaveGeneratedBody(
                                expected = DEFAULT_LOADOUT_BODY,
                                fileName = copilotInstructionsFilePath,
                            )
                        }
                    }
                }
            }

            given("CLAUDE.md was edited after Loadout generated it") {
                val claudeFileWasEdited: ScenarioSeed =
                    initializedRepository.andThen {
                        givenOutputFileWasEdited(claudeFilePath)
                    }

                action("loadout sync is run") {
                    val execution by memoizedCapturedExecution(
                        seed = claudeFileWasEdited,
                        capture = { requireWorkspaceFile(claudeFilePath) },
                    ) { _ ->
                        runCommand("sync")
                    }

                    then("it saves the edited content to a backup") {
                        execution.scenario.requireBackupContent(claudeFilePath) shouldBe execution.captured
                    }

                    then("it restores the composed body in CLAUDE.md") {
                        execution.scenario.shouldHaveGeneratedBody(DEFAULT_LOADOUT_BODY)
                    }

                    then("it reports that it backed up the edits and regenerated CLAUDE.md") {
                        execution.result.shouldContainInStderr(
                            "Warning: Backed up your edits to CLAUDE.md to " +
                                "${execution.scenario.requireBackupPath(claudeFilePath)} and regenerated it."
                        )
                    }
                }
            }

            given("the repository settings list an output file outside the repository") {
                val outputFileOutsideRepository: ScenarioSeed =
                    initializedRepository.andThen {
                        givenRepositorySettingsListOutputFiles("../outside.md", NESTED_CLAUDE_FILE_PATH)
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = outputFileOutsideRepository)

                    then("it warns that the invalid output file was ignored") {
                        execution.result.shouldContainInStderr(INVALID_OUTSIDE_OUTPUT_FILE_WARNING)
                    }

                    then("it writes the remaining output files") {
                        execution.scenario.requireWorkspaceFile(NESTED_CLAUDE_FILE_PATH) shouldStartWith
                            GENERATED_OUTPUT_HEADER
                    }

                    then("it writes nothing outside the repository") {
                        execution.scenario.pathExists("../outside.md") shouldBe false
                    }
                }

                action("loadout init is run again") {
                    val execution by memoizedAction("init", seed = outputFileOutsideRepository)

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }

                    then("it warns that the invalid output file was ignored") {
                        execution.result.shouldContainInStderr(INVALID_OUTSIDE_OUTPUT_FILE_WARNING)
                    }
                }

                action("loadout config is run with --trust-fragment-host raw.githubusercontent.com") {
                    val execution by memoizedAction(
                        "config",
                        "--trust-fragment-host",
                        "raw.githubusercontent.com",
                        seed = outputFileOutsideRepository,
                    )

                    then("it removes the invalid output file from the repository settings") {
                        execution.scenario.requireRepositorySettings().outputFiles.shouldNotBeNull()
                            .shouldContainExactly(NESTED_CLAUDE_FILE_PATH)
                    }
                }
            }

            given("the repository output files are set to CLAUDE.md and docs/CLAUDE.md") {
                val outputFilesIncludeDocs: ScenarioSeed =
                    initializedRepository.andThen {
                        givenRepositoryOutputFilesAreSet(claudeFilePath, NESTED_CLAUDE_FILE_PATH)
                    }

                action("loadout config is run with --fragments-home docs") {
                    val execution by memoizedCapturedExecution(
                        seed = outputFilesIncludeDocs,
                        capture = { requireRepositorySettings() },
                    ) { _ ->
                        runCommand("config", "--fragments-home", "docs")
                    }

                    then("it outputs a validation error naming docs/CLAUDE.md") {
                        execution.result.shouldContainInStderr(
                            "$OUTPUT_FILES_VALIDATION_ERROR: '$NESTED_CLAUDE_FILE_PATH' is inside docs/"
                        )
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }

                    then("it leaves the repository settings unchanged") {
                        execution.scenario.requireRepositorySettings() shouldBe execution.captured
                    }
                }
            }
        }
    }
})
