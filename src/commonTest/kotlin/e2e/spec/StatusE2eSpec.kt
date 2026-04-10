@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import domain.entity.LoadoutConfig
import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.firstFragmentPath
import e2e.support.givenConfigPointsAtDeletedLoadout
import e2e.support.givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition
import e2e.support.givenCurrentLoadoutIsSet
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveNoUnexpectedStderr
import e2e.support.shouldHaveStaleWarning
import e2e.support.shouldNotHaveStaleWarning
import io.kotest.matchers.string.shouldContain

class StatusE2eSpec : E2eBehaviorSuite({
    context("loadout status spec") {
        given("no current loadout is set") {
            action("loadout is run without a subcommand") {
                val execution by memoizedAction()

                then("it outputs that no current loadout is set") {
                    execution.result.stdout.shouldContain("No current loadout set.")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }
            }
        }

        given("a current loadout is set") {
            val currentLoadoutIsSet: ScenarioSeed = {
                givenCurrentLoadoutIsSet(name = "alpha")
            }

            action("loadout is run without a subcommand") {
                val execution by memoizedAction(seed = currentLoadoutIsSet)

                then("it outputs the current loadout name") {
                    execution.result.shouldContainInStdout("Current loadout: alpha")
                    execution.result.shouldHaveNoUnexpectedStderr()
                }

                then("it outputs the fragment count") {
                    execution.result.shouldContainInStdout("Fragments: 1")
                }

                then("it outputs the composed content length") {
                    execution.result.shouldContainInStdout("Composed output:")
                }
            }

            given("the current loadout has a description") {
                action("loadout is run without a subcommand") {
                    val execution by memoizedAction(
                        seed = {
                            givenCurrentLoadoutIsSet(name = "alpha", description = "Primary loadout")
                        }
                    )

                    then("it outputs the current loadout description") {
                        execution.result.shouldContainInStdout("Description: Primary loadout")
                    }
                }
            }

            action("loadout is run without a subcommand and with --verbose") {
                val execution by memoizedAction("--verbose", seed = currentLoadoutIsSet)

                then("it lists the current loadout fragment paths") {
                    execution.result.stdout.shouldContain(firstFragmentPath)
                }
            }

            given("composing the current loadout fails") {
                val brokenCurrentLoadout: ScenarioSeed = {
                    seedLoadout(name = "broken", fragments = listOf(firstFragmentPath))
                    writeConfig(LoadoutConfig(currentLoadoutName = "broken", compositionHash = null))
                }

                action("loadout is run without a subcommand") {
                    val execution by memoizedAction(seed = brokenCurrentLoadout)

                    then("it outputs the composition error") {
                        execution.result.shouldContainInOutput("Fragment not found: $firstFragmentPath")
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }
                }
            }

            given("the current loadout fragments have changed since the last composition") {
                action("loadout is run without a subcommand") {
                    val execution by memoizedAction(
                        seed = {
                            givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(name = "alpha")
                        }
                    )

                    then("it outputs the synchronization warning on stderr") {
                        execution.result.shouldHaveStaleWarning()
                    }
                }
            }
        }

        given("the config points at a deleted loadout") {
            action("loadout is run without a subcommand") {
                val execution by memoizedAction(
                    seed = {
                        givenConfigPointsAtDeletedLoadout(name = "alpha")
                    }
                )

                then("it outputs that the current loadout no longer exists") {
                    execution.result.shouldContainInOutput("Loadout 'alpha' not found")
                    execution.result.shouldNotHaveStaleWarning()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }
    }
})
