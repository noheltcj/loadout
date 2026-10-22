@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import domain.policy.GENERATED_OUTPUT_HEADER
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.agentsFilePath
import e2e.support.andThen
import e2e.support.claudeFilePath
import e2e.support.geminiFilePath
import e2e.support.givenInitializedGitRepository
import e2e.support.givenNoUserConfigDirectoryCanBeDetermined
import e2e.support.givenRepositoryOutputFilesAreSet
import e2e.support.givenUninitializedGitRepository
import e2e.support.givenUserOutputFilesAreSet
import e2e.support.givenUserSettingsFileIsUnreadable
import e2e.support.requireRepositorySettings
import e2e.support.requireWorkspaceFile
import e2e.support.shouldContainInStderr
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

private const val FIRST_RUN_HINT = "Only use some of these agents? Run: loadout config --user --output-files CLAUDE.md"
private const val NOTES_IN_FRAGMENTS_HOME = "fragments/notes.md"
private const val UNREADABLE_USER_SETTINGS = "Could not read the user settings in"

class UserOutputFilesE2eSpec : E2eBehaviorSuite({
    context("user output files spec") {
        given("an initialized repository") {
            val initializedRepository: ScenarioSeed = {
                givenInitializedGitRepository()
            }

            action("loadout config is run with --user --output-files CLAUDE.md") {
                val execution by memoizedCapturedExecution(
                    seed = initializedRepository,
                    capture = { requireRepositorySettings() },
                ) { _ ->
                    runCommand("config", "--user", "--output-files", claudeFilePath)
                }

                then("it saves the list to the user settings file under XDG_CONFIG_HOME") {
                    execution.scenario.readUserSettings()?.outputFiles.shouldNotBeNull()
                        .shouldContainExactly(claudeFilePath)
                }

                then("it writes nothing to the default config directory under HOME") {
                    execution.scenario.pathExists(execution.scenario.homePath(".config/loadout/config.json")) shouldBe
                        false
                }

                then("it leaves the repository settings unchanged") {
                    execution.scenario.requireRepositorySettings() shouldBe execution.captured
                }

                then("it reports where the user settings are stored") {
                    execution.result.shouldContainInStdout("User settings: ${execution.scenario.userSettingsPath}\n")
                }

                then("it tells the user that repositories apply the setting on their next sync") {
                    execution.result.shouldContainInStdout("Repositories apply this on their next sync.")
                }
            }

            action("loadout config is run with --user") {
                val execution by memoizedAction("config", "--user", seed = initializedRepository)

                then("it reports that the user settings file has not been created") {
                    execution.result.shouldContainInStdout(
                        "User settings: ${execution.scenario.userSettingsPath} (not created)"
                    )
                }

                then("it reports that no user output files are set") {
                    execution.result.shouldContainInStdout("Output files: not set")
                }
            }

            action("loadout config is run with --user --default-loadout default") {
                val execution by memoizedAction(
                    "config",
                    "--user",
                    "--default-loadout",
                    "default",
                    seed = initializedRepository,
                )

                then("it outputs an error that --default-loadout is a repository setting") {
                    execution.result.shouldContainInStderr("--default-loadout is a repository setting; remove --user.")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout config is run with --user --output-files .git/notes.md") {
                val execution by memoizedAction(
                    "config",
                    "--user",
                    "--output-files",
                    ".git/notes.md",
                    seed = initializedRepository,
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInStderr("'.git/notes.md' is inside .git/")
                }

                then("it creates no user settings file") {
                    execution.scenario.readUserSettings().shouldBeNull()
                }
            }

            action("loadout config is run with --user and an empty --output-files value") {
                val execution by memoizedAction("config", "--user", "--output-files", "", seed = initializedRepository)

                then("it suggests removing the user setting with --user --unset-output-files") {
                    execution.result.shouldContainInStderr("'loadout config --user --unset-output-files'")
                }
            }

            given("the user output files are set to CLAUDE.md") {
                val userOutputFilesAreClaudeOnly: ScenarioSeed =
                    initializedRepository.andThen {
                        givenUserOutputFilesAreSet(claudeFilePath)
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = userOutputFilesAreClaudeOnly)

                    then("it deletes AGENTS.md") {
                        execution.scenario.readWorkspaceFile(agentsFilePath).shouldBeNull()
                    }

                    then("it deletes GEMINI.md") {
                        execution.scenario.readWorkspaceFile(geminiFilePath).shouldBeNull()
                    }
                }

                action("loadout is run without a subcommand") {
                    val execution by memoizedAction(seed = userOutputFilesAreClaudeOnly)

                    then("it warns that the output files changed since the last sync") {
                        execution.result.shouldContainInStderr(
                            "Warning: Your output files changed since the last sync (removes AGENTS.md, GEMINI.md)."
                        )
                    }
                }

                action("loadout config is run with --user --unset-output-files") {
                    val execution by memoizedAction(
                        "config",
                        "--user",
                        "--unset-output-files",
                        seed = userOutputFilesAreClaudeOnly,
                    )

                    then("it removes the list from the user settings file") {
                        execution.scenario.readUserSettings()?.outputFiles.shouldBeNull()
                    }
                }

                given("the repository output files are set to CLAUDE.md and AGENTS.md") {
                    val repositoryOutputFilesAreOverridden: ScenarioSeed =
                        userOutputFilesAreClaudeOnly.andThen {
                            givenRepositoryOutputFilesAreSet(claudeFilePath, agentsFilePath)
                        }

                    action("loadout sync is run") {
                        val execution by memoizedAction("sync", seed = repositoryOutputFilesAreOverridden)

                        then("it does not write AGENTS.md") {
                            execution.scenario.readWorkspaceFile(agentsFilePath).shouldBeNull()
                        }
                    }

                    action("loadout config is run") {
                        val execution by memoizedAction("config", seed = repositoryOutputFilesAreOverridden)

                        then("it reports CLAUDE.md as the effective output files from the user setting") {
                            execution.result.shouldContainInStdout("Output files: CLAUDE.md (your user setting")
                        }

                        then("it reports that the user setting overrides the repository setting") {
                            execution.result.shouldContainInStdout(
                                "(your user setting; overrides the repository setting CLAUDE.md, AGENTS.md)"
                            )
                        }
                    }
                }
            }

            given("the user output files are set to CLAUDE.md and a file in the fragments home") {
                val userOutputFileInFragmentsHome: ScenarioSeed =
                    initializedRepository.andThen {
                        givenUserOutputFilesAreSet(claudeFilePath, NOTES_IN_FRAGMENTS_HOME)
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = userOutputFileInFragmentsHome)

                    then("it warns that the file in the fragments home was ignored") {
                        execution.result.shouldContainInStderr(
                            "Warning: Ignored output file '$NOTES_IN_FRAGMENTS_HOME' from your user settings"
                        )
                    }

                    then("it does not write the file in the fragments home") {
                        execution.scenario.readWorkspaceFile(NOTES_IN_FRAGMENTS_HOME).shouldBeNull()
                    }

                    then("it deletes AGENTS.md, which the remaining user list leaves out") {
                        execution.scenario.readWorkspaceFile(agentsFilePath).shouldBeNull()
                    }
                }
            }

            given("the user output files are set to only a file in the fragments home") {
                val userOutputFilesAreAllInFragmentsHome: ScenarioSeed =
                    initializedRepository.andThen {
                        givenUserOutputFilesAreSet(NOTES_IN_FRAGMENTS_HOME)
                    }

                action("loadout config is run") {
                    val execution by memoizedAction("config", seed = userOutputFilesAreAllInFragmentsHome)

                    then("it falls back to the built-in default") {
                        execution.result.shouldContainInStdout(
                            "Output files: CLAUDE.md, AGENTS.md, GEMINI.md (built-in default)"
                        )
                    }
                }
            }

            given("the repository output files are set to CLAUDE.md and AGENTS.md") {
                val repositoryOutputFilesAreSet: ScenarioSeed =
                    initializedRepository.andThen {
                        givenRepositoryOutputFilesAreSet(claudeFilePath, agentsFilePath)
                    }

                given("the user settings file is unreadable") {
                    val userSettingsAreUnreadable: ScenarioSeed =
                        repositoryOutputFilesAreSet.andThen {
                            givenUserSettingsFileIsUnreadable()
                        }

                    action("loadout sync is run") {
                        val execution by memoizedAction("sync", seed = userSettingsAreUnreadable)

                        then("it writes CLAUDE.md") {
                            execution.scenario.requireWorkspaceFile(claudeFilePath) shouldStartWith
                                GENERATED_OUTPUT_HEADER
                        }

                        then("it writes AGENTS.md") {
                            execution.scenario.requireWorkspaceFile(agentsFilePath) shouldStartWith
                                GENERATED_OUTPUT_HEADER
                        }

                        then("it warns that the user settings were ignored") {
                            execution.result.shouldContainInStderr(UNREADABLE_USER_SETTINGS)
                        }
                    }

                    action("loadout is run without a subcommand") {
                        val execution by memoizedAction(seed = userSettingsAreUnreadable)

                        then("it prints no user settings warning") {
                            execution.result.stderr shouldNotContain UNREADABLE_USER_SETTINGS
                        }
                    }

                    action("loadout config is run with --user --output-files CLAUDE.md") {
                        val execution by memoizedAction(
                            "config",
                            "--user",
                            "--output-files",
                            claudeFilePath,
                            seed = userSettingsAreUnreadable,
                        )

                        then("it replaces the unreadable user settings file") {
                            execution.scenario.readUserSettings()?.outputFiles.shouldNotBeNull()
                                .shouldContainExactly(claudeFilePath)
                        }

                        then("it warns that the unreadable user settings were replaced") {
                            execution.result.shouldContainInStderr("so Loadout replaced them.")
                        }
                    }
                }
            }
        }

        given("an uninitialized git repository and no user output files") {
            val uninitializedRepository: ScenarioSeed = {
                givenUninitializedGitRepository()
            }

            action("loadout init is run") {
                val execution by memoizedAction("init", seed = uninitializedRepository)

                then("it suggests loadout config --user --output-files") {
                    execution.result.shouldContainInStdout(FIRST_RUN_HINT)
                }
            }

            given("the user output files are set to CLAUDE.md") {
                val userOutputFilesAreClaudeOnly: ScenarioSeed =
                    uninitializedRepository.andThen {
                        givenUserOutputFilesAreSet(claudeFilePath)
                    }

                action("loadout init is run") {
                    val execution by memoizedAction("init", seed = userOutputFilesAreClaudeOnly)

                    then("it writes CLAUDE.md") {
                        execution.scenario.requireWorkspaceFile(claudeFilePath) shouldStartWith GENERATED_OUTPUT_HEADER
                    }

                    then("it does not write AGENTS.md") {
                        execution.scenario.readWorkspaceFile(agentsFilePath).shouldBeNull()
                    }

                    then("it does not write GEMINI.md") {
                        execution.scenario.readWorkspaceFile(geminiFilePath).shouldBeNull()
                    }

                    then("it does not suggest choosing output files") {
                        execution.result.stdout shouldNotContain FIRST_RUN_HINT
                    }
                }
            }
        }

        given("a workspace that is not a repository") {
            action("loadout config is run with --user --output-files CLAUDE.md") {
                val execution by memoizedAction("config", "--user", "--output-files", claudeFilePath)

                then("it saves the list to the user settings file") {
                    execution.scenario.readUserSettings()?.outputFiles.shouldNotBeNull()
                        .shouldContainExactly(claudeFilePath)
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it creates nothing in the workspace") {
                    execution.scenario.listWorkspaceEntries().shouldBeEmpty()
                }
            }
        }

        given("no user config directory can be determined") {
            val userConfigDirectoryIsUndeterminable: ScenarioSeed = {
                givenNoUserConfigDirectoryCanBeDetermined()
            }

            action("loadout config is run with --user") {
                val execution by memoizedAction("config", "--user", seed = userConfigDirectoryIsUndeterminable)

                then("it outputs an error naming XDG_CONFIG_HOME and HOME") {
                    execution.result.shouldContainInStderr("Set XDG_CONFIG_HOME or HOME to an absolute path.")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            given("the workspace is an initialized repository") {
                val initializedRepositoryWithoutUserConfigDirectory: ScenarioSeed =
                    userConfigDirectoryIsUndeterminable.andThen {
                        givenInitializedGitRepository()
                    }

                action("loadout sync is run") {
                    val execution by memoizedAction("sync", seed = initializedRepositoryWithoutUserConfigDirectory)

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }

                    then("it prints no user settings warning") {
                        execution.result.stderr shouldNotContain "user settings"
                    }
                }
            }
        }
    }
})
