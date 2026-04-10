@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.architectFragmentPath
import e2e.support.existingStarterFragmentContent
import e2e.support.givenExistingLoadoutsAlreadyExistBeforeInit
import e2e.support.givenStarterFragmentAlreadyExists
import e2e.support.shouldContainInStdout
import e2e.support.shouldContainLoadoutGitignorePatterns
import e2e.support.shouldContainLocalModeGitignorePatternsExactlyOnce
import e2e.support.shouldContainLocalOnlyGitignorePatterns
import e2e.support.shouldContainSharedModeGitignorePatternsExactlyOnce
import e2e.support.shouldHaveCurrentLoadoutName
import e2e.support.shouldHaveGeneratedFiles
import e2e.support.shouldHaveGitignoreEntries
import e2e.support.shouldHaveNoUnexpectedStderr
import e2e.support.shouldNotIgnoreRepoManagedLoadoutFiles
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class InitE2eSpec : E2eBehaviorSuite({
    context("loadout init spec") {
        given("an isolated workspace") {
            action("loadout init is run in shared mode") {
                val execution by memoizedAction("init")

                then("it adds `# Loadout CLI`, `.loadout.json`, `CLAUDE.md`, and `AGENTS.md` to .gitignore") {
                    execution.scenario.shouldContainLoadoutGitignorePatterns()
                    execution.result.shouldHaveNoUnexpectedStderr()
                }

                then("it does not ignore `.loadouts/` or `fragments/`") {
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
                    execution.scenario.shouldHaveCurrentLoadoutName("default")
                }

                then("it writes CLAUDE.md and AGENTS.md to the default output directory") {
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
                        # Loadout CLI
                        .loadout.json
                        CLAUDE.md
                        AGENTS.md
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

                then("it adds `# Loadout CLI`, `.loadout.json`, `CLAUDE.md`, and `AGENTS.md` to .gitignore") {
                    execution.scenario.shouldContainLoadoutGitignorePatterns()
                }

                then("it adds `# Loadout configuration (local-only)` to .gitignore") {
                    execution.scenario.shouldHaveGitignoreEntries("# Loadout configuration (local-only)")
                }

                then("it adds `.loadouts/` and `fragments/` to .gitignore") {
                    execution.scenario.shouldContainLocalOnlyGitignorePatterns()
                }

                then("it creates the default loadout") {
                    execution.scenario.readLoadout("default").shouldNotBeNull()
                }

                then("it activates the default loadout") {
                    execution.scenario.shouldHaveCurrentLoadoutName("default")
                }

                then("it writes CLAUDE.md and AGENTS.md to the default output directory") {
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
                        # Loadout CLI
                        .loadout.json
                        CLAUDE.md
                        AGENTS.md

                        # Loadout configuration (local-only)
                        .loadouts/
                        fragments/
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
                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = {
                            givenStarterFragmentAlreadyExists()
                        }
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
                action("loadout init is run in shared mode") {
                    val execution by memoizedAction(
                        "init",
                        seed = {
                            givenExistingLoadoutsAlreadyExistBeforeInit()
                        }
                    )

                    then("it does not create a second default loadout") {
                        execution.scenario.readLoadout("default").shouldBeNull()
                        execution.scenario.listLoadoutNames().shouldContainExactly(listOf("existing"))
                    }
                }

                given("the starter fragment does not already exist before init") {
                    action("loadout init is run in shared mode") {
                        val execution by memoizedAction(
                            "init",
                            seed = {
                                givenExistingLoadoutsAlreadyExistBeforeInit()
                            }
                        )

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
        }
    }
})
