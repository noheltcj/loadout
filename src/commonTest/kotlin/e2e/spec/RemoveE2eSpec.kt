@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.action
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenCurrentLoadoutIsSet
import e2e.support.givenValidLoadout
import e2e.support.secondFragmentContent
import e2e.support.secondFragmentPath
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldHaveCurrentLoadoutName
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveGeneratedBody
import e2e.support.shouldNotHaveStaleWarning
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class RemoveE2eSpec : E2eBehaviorSuite({
    context("loadout remove spec") {
        given("the parser receives loadout remove without a loadout name") {
            action("loadout remove is run without a name") {
                val execution by memoizedAction("remove")

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs usage guidance for the missing name argument") {
                    execution.result.shouldContainInOutput("Usage:")
                }
            }
        }

        given("the requested loadout name is blank") {
            action("loadout remove is run with an empty name") {
                val execution by memoizedAction("remove", "")

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the requested loadout does not exist") {
            action("loadout remove is run") {
                val execution by memoizedAction("remove", "missing")

                then("it outputs that the specified loadout was not found") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("legacy fragment-removal syntax is used") {
            action("loadout remove is run with a fragment path and --from") {
                val execution by memoizedAction("remove", firstFragmentPath, "--from", "alpha")

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it rejects the unsupported --from option") {
                    execution.result.shouldContainInOutput("--from")
                }
            }
        }

        given("the target loadout is not current") {
            action("loadout remove is run") {
                val execution by memoizedAction(
                    "remove",
                    "beta",
                    seed = {
                        givenCurrentLoadoutIsSet(
                            name = "alpha",
                            fragments = listOf(firstFragmentPath to firstFragmentContent)
                        )
                        givenValidLoadout(
                            name = "beta",
                            fragments = listOf(secondFragmentPath to secondFragmentContent)
                        )
                    }
                )

                then("it removes the requested loadout definition") {
                    execution.scenario.readLoadout("beta").shouldBeNull()
                }

                then("it keeps the current loadout selection unchanged") {
                    execution.scenario.shouldHaveCurrentLoadoutName("alpha")
                }

                then("it does not rewrite the existing generated files") {
                    execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                }

                then("it reports that the loadout was removed") {
                    execution.result.shouldContainInStdout("Removed loadout 'beta'")
                }
            }
        }

        given("the current loadout is removed while another loadout still exists") {
            action("loadout remove is run") {
                val execution by memoizedAction(
                    "remove",
                    "alpha",
                    seed = {
                        givenCurrentLoadoutIsSet(
                            name = "alpha",
                            fragments = listOf(firstFragmentPath to firstFragmentContent)
                        )
                        givenValidLoadout(
                            name = "beta",
                            fragments = listOf(secondFragmentPath to secondFragmentContent)
                        )
                    }
                )

                then("it removes the loadout definition") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it keeps the other loadout definition") {
                    execution.scenario.readLoadout("beta").shouldNotBeNull()
                }

                then("it clears the current loadout name") {
                    execution.scenario.shouldHaveCurrentLoadoutName(null)
                }

                then("it clears the stored composition hash") {
                    execution.scenario.readConfig()?.compositionHash shouldBe null
                }

                then("it leaves the generated files untouched") {
                    execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                }

                then("it reports that the current selection was cleared") {
                    execution.result.shouldContainInStdout("Cleared the current loadout selection.")
                }

                then("loadout status returns to the no-current state") {
                    val statusResult = execution.scenario.runCommand()
                    statusResult.shouldHaveExitCode(0)
                    statusResult.stdout.shouldContain("No current loadout set.")
                }

                then("follow-up read-only commands do not emit a stale-warning") {
                    execution.scenario.runCommand("list").shouldNotHaveStaleWarning()
                }
            }
        }

        given("the current loadout is the only remaining loadout") {
            action("loadout remove is run") {
                val execution by memoizedAction(
                    "remove",
                    "alpha",
                    seed = {
                        givenCurrentLoadoutIsSet(
                            name = "alpha",
                            fragments = listOf(firstFragmentPath to firstFragmentContent)
                        )
                    }
                )

                then("it removes the last remaining loadout") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it leaves the workspace in the empty-list state") {
                    execution.scenario.runCommand("list").stdout.shouldContain("No loadouts found.")
                }
            }
        }
    }
})
