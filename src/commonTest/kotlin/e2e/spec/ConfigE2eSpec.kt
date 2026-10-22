@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import domain.entity.RepositorySettings
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenGitRepositoryExists
import e2e.support.repositorySettingsPath
import e2e.support.requireRepositorySettings
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveActiveLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveRepositoryDefaultLoadoutName
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ConfigE2eSpec : E2eBehaviorSuite({
    context("loadout config spec") {
        given("a shared git repository has been initialized") {
            val initializedSharedGitRepository: ScenarioSeed = {
                givenGitRepositoryExists()
                runCommand("init")
            }

            action("loadout config is run without flags") {
                val execution by memoizedAction("config", seed = initializedSharedGitRepository)

                then("it reports the current repo default loadout") {
                    execution.result.shouldContainInStdout("Default loadout: default")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it reports the fragment copy defaults") {
                    execution.result.shouldContainInStdout(
                        "Fragments home: fragments",
                        "Trusted fragment source hosts: none",
                        "Maximum fragment copy size: 200000 bytes",
                    )
                }
            }

            given("another valid loadout exists") {
                val anotherValidLoadoutExists: ScenarioSeed =
                    initializedSharedGitRepository.andThen {
                        seedFragment(firstFragmentPath, firstFragmentContent)
                        seedLoadout(name = "alpha", fragments = listOf(firstFragmentPath))
                    }

                action("loadout config is run with --default-loadout and that loadout name") {
                    val execution by memoizedAction(
                        "config",
                        "--default-loadout",
                        "alpha",
                        seed = anotherValidLoadoutExists
                    )

                    then("it updates the repo default loadout selection") {
                        execution.scenario.shouldHaveRepositoryDefaultLoadoutName("alpha")
                    }

                    then("it reports the new repo default loadout") {
                        execution.result.shouldContainInStdout("Default loadout: alpha")
                    }

                    then("it leaves local state unchanged") {
                        execution.scenario.shouldHaveActiveLoadoutName("default")
                    }
                }
            }

            action("loadout config is run with --unset-default-loadout") {
                val execution by memoizedAction(
                    "config",
                    "--unset-default-loadout",
                    seed = initializedSharedGitRepository
                )

                then("it clears the repo default loadout selection") {
                    execution.scenario.shouldHaveRepositoryDefaultLoadoutName(null)
                }

                then("it reports that no repo default loadout is set") {
                    execution.result.shouldContainInStdout("No repository default loadout set.")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }
            }

            action("loadout config is run with both --default-loadout and --unset-default-loadout") {
                val execution by memoizedAction(
                    "config",
                    "--default-loadout",
                    "default",
                    "--unset-default-loadout",
                    seed = initializedSharedGitRepository
                )

                then("it rejects the conflicting request") {
                    execution.result.shouldContainInOutput("Change one repository setting per invocation.")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout config is run with --default-loadout for a missing loadout") {
                val execution by memoizedAction(
                    "config",
                    "--default-loadout",
                    "missing",
                    seed = initializedSharedGitRepository
                )

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it leaves the repo default loadout unchanged") {
                    execution.scenario.shouldHaveRepositoryDefaultLoadoutName("default")
                }
            }
        }

        given("the workspace has no repository settings file") {
            action("loadout config is run with --fragments-home") {
                val execution by memoizedAction("config", "--fragments-home", "guidance/library")

                then("it persists the custom fragments home") {
                    execution.scenario.requireRepositorySettings().fragmentsHome shouldBe "guidance/library"
                }
            }

            action("loadout config is run with --trust-fragment-host in mixed case") {
                val execution by memoizedAction(
                    "config",
                    "--trust-fragment-host",
                    "RAW.GITHUBUSERCONTENT.COM",
                )

                then("it persists the normalized host") {
                    execution.scenario.requireRepositorySettings().trustedFragmentSourceHosts
                        .shouldContainExactly("raw.githubusercontent.com")
                }
            }

            action("loadout config is run with a fragments home that escapes the repository") {
                val execution by memoizedAction("config", "--fragments-home", "../outside")

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("safe repository-relative directory")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it writes no repository settings file") {
                    execution.scenario.readRepositorySettings().shouldBeNull()
                }
            }
        }

        given("the repository settings file is malformed") {
            val malformedSettings: ScenarioSeed = {
                writeWorkspaceFile(repositorySettingsPath, "{ this is not json")
            }

            action("loadout config is run with a repair value") {
                val execution by memoizedAction(
                    "config",
                    "--fragments-home",
                    "guidance/library",
                    seed = malformedSettings,
                )

                then("it warns that safe defaults were used") {
                    execution.result.shouldContainInOutput("safe defaults are in use")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it replaces the malformed file with validated settings") {
                    execution.scenario.requireRepositorySettings().fragmentsHome shouldBe "guidance/library"
                }
            }
        }

        given("the repository settings contain invalid field values") {
            val invalidSettings: ScenarioSeed = {
                writeRepositorySettings(
                    RepositorySettings(
                        fragmentsHome = "../outside",
                        trustedFragmentSourceHosts = listOf("example.com", "169.254.169.254"),
                        maximumFragmentCopyBytes = 0,
                    )
                )
            }

            action("loadout config is run without updates") {
                val execution by memoizedAction("config", seed = invalidSettings)

                then("it reports every repaired field") {
                    execution.result.shouldContainInOutput(
                        "Invalid fragments home '../outside' was replaced",
                        "Invalid trusted fragment host '169.254.169.254' was removed",
                        "Invalid fragment copy limit 0 was replaced",
                    )
                }

                then("it displays safe settings") {
                    execution.result.shouldContainInStdout(
                        "Fragments home: fragments",
                        "Trusted fragment source hosts: example.com",
                        "Maximum fragment copy size: 200000 bytes",
                    )
                }
            }
        }
    }
})
