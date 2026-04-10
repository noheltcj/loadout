@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.firstFragmentPath
import e2e.support.givenConfigPointsAtDeletedLoadout
import e2e.support.givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition
import e2e.support.givenTwoValidLoadoutsExist
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveExitCode
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import e2e.support.shouldHaveStaleWarning
import e2e.support.shouldNotHaveStaleWarning

class ListE2eSpec : E2eBehaviorSuite({
    context("loadout list spec") {
        given("no loadouts exist") {
            action("loadout list is run") {
                val execution by memoizedAction("list")

                then("it outputs the empty-state message") {
                    execution.result.stdout.shouldContain("No loadouts found.")
                }

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }
            }
        }

        given("loadouts exist") {
            val loadoutsExist: ScenarioSeed = {
                givenTwoValidLoadoutsExist()
            }

            action("loadout list is run") {
                val execution by memoizedAction("list", seed = loadoutsExist)

                then("it outputs every loadout name") {
                    execution.result.shouldContainInStdout("alpha", "beta")
                }

                then("it outputs each loadout's fragment count") {
                    execution.result.shouldContainInStdout("Fragments: 1")
                }

                then("it outputs each description when present") {
                    execution.result.shouldContainInStdout("Alpha loadout", "Beta loadout")
                }
            }

            action("loadout list is run with --verbose") {
                val execution by memoizedAction("list", "--verbose", seed = loadoutsExist)

                then("it lists fragment paths under each loadout") {
                    execution.result.stdout.shouldContain(firstFragmentPath)
                }
            }
        }

        given("the current loadout fragments have changed since the last composition") {
            action("loadout list is run") {
                val execution by memoizedAction(
                    "list",
                    seed = {
                        givenCurrentLoadoutFragmentsHaveChangedSinceLastComposition(name = "alpha")
                    }
                )

                then("it warns after listing loadouts") {
                    execution.result.stdout.shouldContain("Available loadouts:")
                    execution.result.shouldHaveStaleWarning()
                }
            }
        }

        given("the config points at a deleted loadout") {
            action("loadout list is run") {
                val execution by memoizedAction(
                    "list",
                    seed = {
                        givenConfigPointsAtDeletedLoadout(name = "alpha")
                    }
                )

                then("it exits with result 0") {
                    execution.result.shouldHaveExitCode(0)
                }

                then("it does not emit a misleading synchronization warning for a loadout that no longer exists") {
                    execution.result.shouldNotHaveStaleWarning()
                }
            }
        }
    }
})
