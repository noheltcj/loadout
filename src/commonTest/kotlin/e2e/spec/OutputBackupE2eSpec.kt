@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import cli.Constants
import domain.policy.GENERATED_OUTPUT_HEADER
import e2e.support.CommandResult
import e2e.support.E2eBehaviorSuite
import e2e.support.E2eScenario
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.agentsFilePath
import e2e.support.andThen
import e2e.support.claudeFilePath
import e2e.support.firstFragmentContent
import e2e.support.givenBackupFoldersAlreadyExistForTheCurrentTime
import e2e.support.givenDirectoryExistsOutsideWorkspace
import e2e.support.givenFileExistsOutsideWorkspace
import e2e.support.givenHandWrittenAgentFileExists
import e2e.support.givenLocalStateIsUnreadable
import e2e.support.givenSymbolicLinkExists
import e2e.support.givenTwoLoadoutsExistAndAlphaIsCurrent
import e2e.support.givenUninitializedGitRepository
import e2e.support.handWrittenAgentGuidance
import e2e.support.requireBackupContent
import e2e.support.requireBackupPath
import e2e.support.requireGeneratedBody
import e2e.support.requireWorkspaceFile
import e2e.support.secondFragmentContent
import e2e.support.shouldContainInStderr
import e2e.support.shouldHaveActiveLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldHaveNoBackups
import e2e.support.shouldHideDirectoryFromGitStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith

private const val HAND_WRITTEN_AGENTS_GUIDANCE = "# Agent guidance\n\nRun the tests before committing.\n"
private const val OUTSIDE_BACKUPS_PATH = "outside-backups"
private const val OUTSIDE_TARGET_PATH = "outside/notes.md"
private const val OUTSIDE_TARGET_CONTENT = "## Notes kept outside the repository\n\nShared across projects.\n"
private const val SUGGESTED_COMMAND_INDENT = "  "

private fun E2eScenario.runSuggestedCommands(output: String): CommandResult =
    output
        .lineSequence()
        .filter { line -> line.startsWith(SUGGESTED_COMMAND_INDENT) }
        .map(String::trim)
        .map { command ->
            if (command.startsWith("loadout ")) {
                runCommand(*command.removePrefix("loadout ").split(' ').toTypedArray())
            } else {
                runExternalCommand("sh", "-c", command)
            }.also { result ->
                check(result.exitCode == 0) { "Suggested command '$command' failed:\n${result.output}" }
            }
        }
        .last()

class OutputBackupE2eSpec : E2eBehaviorSuite({
    context("output backup spec") {
        given("an uninitialized git repository with a hand-written CLAUDE.md") {
            val handWrittenClaudeFile: ScenarioSeed = {
                givenUninitializedGitRepository()
                givenHandWrittenAgentFileExists()
            }

            action("loadout init is run") {
                val execution by memoizedAction("init", seed = handWrittenClaudeFile)

                then("it saves the hand-written content to a backup under .loadout-backups") {
                    execution.scenario.requireBackupContent(claudeFilePath) shouldBe handWrittenAgentGuidance
                }

                then("it writes the generated CLAUDE.md") {
                    execution.scenario.requireWorkspaceFile(claudeFilePath) shouldStartWith GENERATED_OUTPUT_HEADER
                }

                then("it reports the backup path on stderr") {
                    execution.result.shouldContainInStderr(execution.scenario.requireBackupPath(claudeFilePath))
                }

                then("it suggests moving the backup into a fragment") {
                    execution.result.shouldContainInStderr(
                        "  mv ${execution.scenario.requireBackupPath(claudeFilePath)} fragments/claude.md"
                    )
                }

                then("it suggests linking that fragment to the default loadout") {
                    execution.result.shouldContainInStderr("loadout fragment link fragments/claude.md --to default")
                }

                then("it leaves the backup out of git status") {
                    execution.scenario.shouldHideDirectoryFromGitStatus(Constants.BACKUPS_DIR)
                }
            }

            action("loadout init is run and its suggested commands are then run verbatim") {
                val execution by memoizedExecution(seed = handWrittenClaudeFile) {
                    runSuggestedCommands(runCommand("init").stderr)
                }

                then("it composes the hand-written guidance into CLAUDE.md") {
                    execution.scenario.requireGeneratedBody() shouldContain "Prefer small pull requests."
                }
            }

            given("a hand-written AGENTS.md also exists") {
                val handWrittenAgentFiles: ScenarioSeed =
                    handWrittenClaudeFile.andThen {
                        givenHandWrittenAgentFileExists(agentsFilePath, HAND_WRITTEN_AGENTS_GUIDANCE)
                    }

                action("loadout init is run") {
                    val execution by memoizedAction("init", seed = handWrittenAgentFiles)

                    then("it reports the backup of AGENTS.md") {
                        execution.result.shouldContainInStderr(execution.scenario.requireBackupPath(agentsFilePath))
                    }

                    then("it suggests creating the fragments directory once") {
                        execution.result.stderr.lines().count { line -> line.startsWith("  mkdir -p ") } shouldBe 1
                    }

                    then("it suggests running loadout sync once") {
                        execution.result.stderr.lines().count { line -> line == "  loadout sync" } shouldBe 1
                    }
                }

                action("loadout init is run and its suggested commands are then run verbatim") {
                    val execution by memoizedExecution(seed = handWrittenAgentFiles) {
                        runSuggestedCommands(runCommand("init").stderr)
                    }

                    then("it composes the hand-written CLAUDE.md guidance into CLAUDE.md") {
                        execution.scenario.requireGeneratedBody() shouldContain "Prefer small pull requests."
                    }

                    then("it composes the hand-written AGENTS.md guidance into CLAUDE.md") {
                        execution.scenario.requireGeneratedBody() shouldContain "Run the tests before committing."
                    }
                }
            }

            given("fragments/claude.md already exists") {
                val fragmentNameIsTaken: ScenarioSeed =
                    handWrittenClaudeFile.andThen {
                        seedFragment("fragments/claude.md", "## Existing\n\nExisting fragment")
                    }

                action("loadout init is run") {
                    val execution by memoizedAction("init", seed = fragmentNameIsTaken)

                    then("it suggests a fragment path that does not replace the existing fragment") {
                        execution.result.shouldContainInStderr(
                            "mv ${execution.scenario.requireBackupPath(claudeFilePath)} fragments/claude-2.md"
                        )
                    }
                }
            }
        }

        given("two loadouts exist and alpha is current") {
            val alphaIsCurrent: ScenarioSeed = {
                givenTwoLoadoutsExistAndAlphaIsCurrent()
            }

            action("loadout use is run with beta") {
                val execution by memoizedAction("use", "beta", seed = alphaIsCurrent)

                then("it creates no backup") {
                    execution.scenario.shouldHaveNoBackups()
                }
            }

            given("CLAUDE.md was replaced with hand-written content") {
                val claudeFileWasReplaced: ScenarioSeed =
                    alphaIsCurrent.andThen {
                        givenHandWrittenAgentFileExists()
                    }

                action("loadout use is run with beta") {
                    val execution by memoizedAction("use", "beta", seed = claudeFileWasReplaced)

                    then("it saves the hand-written content to a backup") {
                        execution.scenario.requireBackupContent(claudeFilePath) shouldBe handWrittenAgentGuidance
                    }

                    then("it writes beta's composed body to CLAUDE.md") {
                        execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                    }
                }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = claudeFileWasReplaced)

                    then("it saves the hand-written content to a backup") {
                        execution.scenario.requireBackupContent(claudeFilePath) shouldBe handWrittenAgentGuidance
                    }

                    then("it restores alpha's composed body in CLAUDE.md") {
                        execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                    }
                }

                given("a backup folder already exists for the current time") {
                    val backupFolderIsTaken: ScenarioSeed =
                        claudeFileWasReplaced.andThen {
                            givenBackupFoldersAlreadyExistForTheCurrentTime()
                        }

                    action("loadout use is run with beta") {
                        val execution by memoizedAction("use", "beta", seed = backupFolderIsTaken)

                        then("it saves the backup in a folder with a numeric suffix") {
                            execution.scenario.requireBackupPath(claudeFilePath) shouldMatch
                                Regex("""\.loadout-backups/\d{8}T\d{6}Z-2/CLAUDE\.md\.backup""")
                        }
                    }
                }

                given(".loadout-backups is a symbolic link to a directory outside the repository") {
                    val backupsDirectoryIsSymbolicLink: ScenarioSeed =
                        claudeFileWasReplaced.andThen {
                            val target = givenDirectoryExistsOutsideWorkspace(OUTSIDE_BACKUPS_PATH)
                            givenSymbolicLinkExists(Constants.BACKUPS_DIR, target)
                        }

                    action("loadout use is run with beta") {
                        val execution by memoizedAction("use", "beta", seed = backupsDirectoryIsSymbolicLink)

                        then("it exits with result 1") {
                            execution.result.shouldHaveExitCode(1)
                        }

                        then("it reports that it never writes through the symbolic link") {
                            execution.result.shouldContainInStderr(
                                "${Constants.BACKUPS_DIR} is a symbolic link, and Loadout never writes through one"
                            )
                        }

                        then("it writes nothing into the link's target") {
                            execution.scenario.listHomeFiles(OUTSIDE_BACKUPS_PATH).shouldBeEmpty()
                        }

                        then("it leaves CLAUDE.md unchanged") {
                            execution.scenario.readWorkspaceFile(claudeFilePath) shouldBe handWrittenAgentGuidance
                        }
                    }
                }

                given("a file occupies the .loadout-backups path") {
                    val backupsPathIsAFile: ScenarioSeed =
                        claudeFileWasReplaced.andThen {
                            writeWorkspaceFile(Constants.BACKUPS_DIR, "not a directory\n")
                        }

                    action("loadout use is run with beta") {
                        val execution by memoizedAction("use", "beta", seed = backupsPathIsAFile)

                        then("it exits with result 1") {
                            execution.result.shouldHaveExitCode(1)
                        }

                        then("it leaves CLAUDE.md unchanged") {
                            execution.scenario.readWorkspaceFile(claudeFilePath) shouldBe handWrittenAgentGuidance
                        }

                        then("it leaves the other output files unchanged") {
                            execution.scenario.shouldHaveGeneratedBody(firstFragmentContent, agentsFilePath)
                        }
                    }
                }
            }

            given("the local state cannot be read") {
                val localStateIsUnreadable: ScenarioSeed =
                    alphaIsCurrent.andThen {
                        givenLocalStateIsUnreadable()
                    }

                action("loadout use is run with beta") {
                    val execution by memoizedAction("use", "beta", seed = localStateIsUnreadable)

                    then("it creates no backup") {
                        execution.scenario.shouldHaveNoBackups()
                    }

                    then("it writes beta's composed body to CLAUDE.md") {
                        execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                    }

                    then("it marks beta as current") {
                        execution.scenario.shouldHaveActiveLoadoutName("beta")
                    }
                }
            }

            given("CLAUDE.md was replaced with a symbolic link to a file outside the repository") {
                val claudeFileIsSymbolicLink: ScenarioSeed =
                    alphaIsCurrent.andThen {
                        val target = givenFileExistsOutsideWorkspace(OUTSIDE_TARGET_PATH, OUTSIDE_TARGET_CONTENT)
                        deleteWorkspaceFile(claudeFilePath)
                        givenSymbolicLinkExists(claudeFilePath, target)
                    }

                action("loadout use is run with beta") {
                    val execution by memoizedAction("use", "beta", seed = claudeFileIsSymbolicLink)

                    then("it leaves the link's target unchanged") {
                        execution.scenario.readHomeFile(OUTSIDE_TARGET_PATH) shouldBe OUTSIDE_TARGET_CONTENT
                    }

                    then("it replaces the link with a regular file") {
                        execution.scenario.workspacePathIsSymbolicLink(claudeFilePath) shouldBe false
                    }

                    then("it writes beta's composed body to CLAUDE.md") {
                        execution.scenario.shouldHaveGeneratedBody(secondFragmentContent)
                    }

                    then("it saves the target's content to a backup") {
                        execution.scenario.requireBackupContent(claudeFilePath) shouldBe OUTSIDE_TARGET_CONTENT
                    }

                    then("it names the link's target in the warning") {
                        execution.result.stderr shouldContain
                            Regex("""left its target, /\S*/$OUTSIDE_TARGET_PATH, unchanged""")
                    }
                }
            }
        }
    }
})
