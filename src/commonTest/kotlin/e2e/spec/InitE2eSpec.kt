@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import cli.Constants
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.architectFragmentPath
import e2e.support.existingStarterFragmentContent
import e2e.support.givenExistingLoadoutsAlreadyExistBeforeInit
import e2e.support.givenGitRepositoryExists
import e2e.support.givenStarterFragmentAlreadyExists
import e2e.support.givenTwoValidLoadoutsExist
import e2e.support.hooksDirectoryPath
import e2e.support.postCheckoutHookPath
import e2e.support.postMergeHookPath
import e2e.support.requireWorkspaceFile
import e2e.support.shouldContainInStdout
import e2e.support.shouldContainLoadoutGitignorePatterns
import e2e.support.shouldContainLocalModeGitignorePatternsExactlyOnce
import e2e.support.shouldContainLocalOnlyGitignorePatterns
import e2e.support.shouldContainRepositorySettingsGitignorePatterns
import e2e.support.shouldContainSharedModeGitignorePatternsExactlyOnce
import e2e.support.shouldHaveActiveLoadoutName
import e2e.support.shouldHaveExecutableWorkspaceFile
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldHaveGitLocalConfig
import e2e.support.shouldHaveGitignoreEntries
import e2e.support.shouldHaveNoUnexpectedStderr
import e2e.support.shouldHaveRepositoryDefaultLoadoutName
import e2e.support.shouldNotHaveGitignoreEntries
import e2e.support.shouldNotIgnoreRepoManagedLoadoutFiles
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private data class HookFileContentsCapture(
    val postCheckoutHookContent: String,
    val postMergeHookContent: String,
)

class InitE2eSpec : E2eBehaviorSuite({
    context("loadout init spec") {
        given("an isolated workspace") {
            action("loadout init is run in shared mode") {
                val execution by memoizedAction("init")

                then(
                    "it adds local runtime state and generated markdown outputs to .gitignore"
                ) {
                    execution.scenario.shouldContainLoadoutGitignorePatterns()
                    execution.result.shouldHaveNoUnexpectedStderr()
                }

                then("it does not ignore repository-managed files") {
                    execution.scenario.shouldNotIgnoreRepoManagedLoadoutFiles()
                }

                then("it creates fragments/loadout-architect.md") {
                    execution.scenario.readWorkspaceFile(architectFragmentPath).shouldNotBeNull()
                }

                then("the starter fragment content documents link, unlink, and remove") {
                    val starterFragment = execution.scenario.readWorkspaceFile(architectFragmentPath).shouldNotBeNull()
                    starterFragment.shouldContain("`loadout remove <name>`")
                    starterFragment.shouldContain("`loadout link <fragment-path> --to <loadout>`")
                    starterFragment.shouldContain("`loadout unlink <fragment-path> --from <loadout>`")
                }

                then("it creates the default loadout") {
                    execution.scenario.readLoadout("default").shouldNotBeNull()
                }

                then("it activates the default loadout") {
                    execution.scenario.shouldHaveActiveLoadoutName("default")
                }

                then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md to the default output directory") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }

                then("it prints the shared-mode completion note") {
                    execution.result.stdout.shouldContain(
                        "Note: In shared mode, loadout configurations are committed and shared with your team."
                    )
                }
            }

            given(".gitignore already contains the shared-mode Loadout patterns") {
                val sharedModeGitignoreAlreadyConfigured: ScenarioSeed = {
                    writeWorkspaceFile(
                        ".gitignore",
                        """
                        # Loadout local runtime state
                        ${Constants.LOCAL_LOADOUT_STATE_FILE}
                        ${Constants.CLAUDE_MD}
                        ${Constants.AGENTS_MD}
                        ${Constants.GEMINI_MD}
                        """.trimIndent()
                    )
                }

                action("loadout init is run in shared mode") {
                    val execution by memoizedAction("init", seed = sharedModeGitignoreAlreadyConfigured)

                    then("it reports that .gitignore is already configured for shared mode") {
                        execution.result.stdout.shouldContain("already configured for Loadout (Shared mode)")
                    }

                    then("it does not duplicate the shared-mode patterns") {
                        execution.scenario.shouldContainSharedModeGitignorePatternsExactlyOnce()
                    }
                }
            }

            action("loadout init is run in local mode") {
                val execution by memoizedAction("init", "--mode", "local")

                then(
                    "it adds local runtime state and generated markdown outputs to .gitignore"
                ) {
                    execution.scenario.shouldContainLoadoutGitignorePatterns()
                }

                then("it adds repository settings to .gitignore") {
                    execution.scenario.shouldContainRepositorySettingsGitignorePatterns()
                }

                then("it adds the local-only definitions header to .gitignore") {
                    execution.scenario.shouldHaveGitignoreEntries("# Loadout local-only definitions")
                }

                then("it adds `.loadouts/` and `fragments/` to .gitignore") {
                    execution.scenario.shouldContainLocalOnlyGitignorePatterns()
                }

                then("it creates the default loadout") {
                    execution.scenario.readLoadout("default").shouldNotBeNull()
                }

                then("it activates the default loadout") {
                    execution.scenario.shouldHaveActiveLoadoutName("default")
                }

                then("it writes CLAUDE.md, AGENTS.md, and GEMINI.md to the default output directory") {
                    execution.scenario.shouldHaveGeneratedFiles()
                }

                then("it prints the local-mode completion note") {
                    execution.result.stdout.shouldContain(
                        "Note: In local mode, loadout configurations are not shared with your team."
                    )
                }
            }

            given(".gitignore already contains the local-mode Loadout patterns") {
                val localModeGitignoreAlreadyConfigured: ScenarioSeed = {
                    writeWorkspaceFile(
                        ".gitignore",
                        """
                        # Loadout local runtime state
                        ${Constants.LOCAL_LOADOUT_STATE_FILE}
                        ${Constants.CLAUDE_MD}
                        ${Constants.AGENTS_MD}
                        ${Constants.GEMINI_MD}

                        # Loadout repository settings
                        ${Constants.REPOSITORY_SETTINGS_FILE}

                        # Loadout local-only definitions
                        ${Constants.LOADOUTS_DIR}/
                        ${Constants.FRAGMENTS_DIR}/
                        """.trimIndent()
                    )
                }

                action("loadout init is run in local mode") {
                    val execution by memoizedAction(
                        "init",
                        "--mode",
                        "local",
                        seed = localModeGitignoreAlreadyConfigured
                    )

                    then("it reports that .gitignore is already configured for local mode") {
                        execution.result.stdout.shouldContain("already configured for Loadout (Local mode)")
                    }

                    then("it does not duplicate the local-mode patterns") {
                        execution.scenario.shouldContainLocalModeGitignorePatternsExactlyOnce()
                    }
                }
            }

            given("the starter fragment already exists before init") {
                val starterFragmentAlreadyExistsBeforeInit: ScenarioSeed = {
                    givenStarterFragmentAlreadyExists()
                }

                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = starterFragmentAlreadyExistsBeforeInit
                    )

                    then("it reports that the starter fragment already exists") {
                        execution.result.shouldContainInStdout("Starter fragment already exists")
                    }

                    then("it does not overwrite the existing fragment content") {
                        execution.scenario.readWorkspaceFile(architectFragmentPath) shouldBe
                            existingStarterFragmentContent
                    }
                }
            }

            given("existing loadouts already exist before init") {
                val existingLoadoutsAlreadyExistBeforeInit: ScenarioSeed = {
                    givenExistingLoadoutsAlreadyExistBeforeInit()
                }

                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = existingLoadoutsAlreadyExistBeforeInit
                    )

                    then("it does not create a second default loadout") {
                        execution.scenario.readLoadout("default").shouldBeNull()
                        execution.scenario.listLoadoutNames().shouldContainExactly(listOf("existing"))
                    }
                    then("it prints guidance for adding the starter fragment to an existing loadout") {
                        execution.result.stdout.shouldContain(
                            "Existing loadouts found. Link the new fragment with:"
                        )
                        execution.result.stdout.shouldContain(
                            "loadout link fragments/loadout-architect.md --to <loadout-name>"
                        )
                    }
                }
            }
        }

        given("an isolated git repository") {
            val isolatedGitRepository: ScenarioSeed = {
                givenGitRepositoryExists()
            }

            action("loadout init is run in shared mode") {
                val execution by memoizedAction("init", seed = isolatedGitRepository)

                then("it installs tracked post-checkout and post-merge hook scripts") {
                    execution.scenario.readWorkspaceFile(postCheckoutHookPath).shouldNotBeNull()
                    execution.scenario.readWorkspaceFile(postMergeHookPath).shouldNotBeNull()
                }

                then("it marks the installed hook scripts executable") {
                    execution.scenario.shouldHaveExecutableWorkspaceFile(postCheckoutHookPath)
                    execution.scenario.shouldHaveExecutableWorkspaceFile(postMergeHookPath)
                }

                then("it points git at the tracked hooks directory") {
                    execution.scenario.shouldHaveGitLocalConfig("core.hooksPath", hooksDirectoryPath)
                }

                then("it seeds the repo default loadout as default") {
                    execution.scenario.shouldHaveRepositoryDefaultLoadoutName("default")
                }

                then("it does not add the tracked hooks directory to .gitignore") {
                    execution.scenario.shouldNotHaveGitignoreEntries("$hooksDirectoryPath/")
                }
            }

            given("shared init has already installed the tracked hooks") {
                val sharedInitHasAlreadyInstalledTrackedHooks: ScenarioSeed =
                    isolatedGitRepository.andThen {
                        runCommand("init")
                    }

                action("loadout init is run in shared mode again") {
                    val execution by memoizedCapturedExecution(
                        seed = sharedInitHasAlreadyInstalledTrackedHooks,
                        capture = {
                            HookFileContentsCapture(
                                postCheckoutHookContent = requireWorkspaceFile(postCheckoutHookPath),
                                postMergeHookContent = requireWorkspaceFile(postMergeHookPath),
                            )
                        },
                    ) { _ ->
                        runCommand("init")
                    }

                    then("it keeps the post-checkout hook content stable") {
                        execution.scenario.readWorkspaceFile(postCheckoutHookPath) shouldBe
                            execution.captured.postCheckoutHookContent
                    }

                    then("it keeps the post-merge hook content stable") {
                        execution.scenario.readWorkspaceFile(postMergeHookPath) shouldBe
                            execution.captured.postMergeHookContent
                    }

                    then("it keeps git configured to the tracked hooks directory only once") {
                        execution.scenario
                            .runGit("config", "--local", "--get-all", "core.hooksPath")
                            .stdout
                            .trim() shouldBe hooksDirectoryPath
                    }
                }
            }

            given("one existing loadout already exists before init") {
                val oneExistingLoadoutAlreadyExistsBeforeInit: ScenarioSeed =
                    isolatedGitRepository.andThen {
                        givenExistingLoadoutsAlreadyExistBeforeInit()
                    }

                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = oneExistingLoadoutAlreadyExistsBeforeInit
                    )

                    then("it uses the existing loadout as the repo default") {
                        execution.scenario.shouldHaveRepositoryDefaultLoadoutName("existing")
                    }
                }
            }

            given("multiple existing loadouts already exist before init") {
                val multipleExistingLoadoutsAlreadyExistBeforeInit: ScenarioSeed =
                    isolatedGitRepository.andThen {
                        givenTwoValidLoadoutsExist()
                    }

                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = multipleExistingLoadoutsAlreadyExistBeforeInit
                    )

                    then("it leaves the repo default unset") {
                        execution.scenario.shouldHaveRepositoryDefaultLoadoutName(null)
                    }

                    then("it prints follow-up guidance for choosing a repo default loadout") {
                        execution.result.stdout.shouldContain("loadout config --default-loadout <name>")
                    }
                }
            }

            given("a foreign hooks path is already configured") {
                val foreignHooksPathIsAlreadyConfigured: ScenarioSeed =
                    isolatedGitRepository.andThen {
                        runGit("config", "core.hooksPath", ".foreign-hooks").shouldHaveExitCode(0)
                        writeWorkspaceFile(".foreign-hooks/post-checkout", "#!/bin/sh\nexit 0\n")
                        setWorkspaceFileExecutable(".foreign-hooks/post-checkout")
                    }

                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = foreignHooksPathIsAlreadyConfigured
                    )

                    then("it leaves the foreign hooks path configured") {
                        execution.scenario.shouldHaveGitLocalConfig("core.hooksPath", ".foreign-hooks")
                    }

                    then("it does not install tracked loadout-managed hooks") {
                        execution.scenario.readWorkspaceFile(postCheckoutHookPath).shouldBeNull()
                        execution.scenario.readWorkspaceFile(postMergeHookPath).shouldBeNull()
                    }

                    then("it prints manual follow-up guidance instead of taking over hooks") {
                        execution.result.stdout.shouldContain("core.hooksPath")
                        execution.result.stdout.shouldContain(".githooks")
                    }
                }
            }
        }
    }
})
