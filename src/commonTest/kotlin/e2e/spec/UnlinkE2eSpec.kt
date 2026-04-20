@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
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
import e2e.support.shouldHaveLoadoutFragments
import e2e.support.shouldHaveStaleWarning

class UnlinkE2eSpec : E2eBehaviorSuite({
    context("loadout unlink spec") {
        given("the requested fragment path exists in the workspace") {
            val requestedFragmentExists: ScenarioSeed = {
                seedFragment(firstFragmentPath, firstFragmentContent)
            }

            action("loadout unlink is run without --from") {
                val execution by memoizedAction(
                    "unlink",
                    firstFragmentPath,
                    seed = requestedFragmentExists
                )

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs parser guidance for the missing --from option") {
                    execution.result.shouldContainInOutput("--from")
                }
            }

            action("loadout unlink is run with --from referencing a missing loadout") {
                val execution by memoizedAction(
                    "unlink",
                    firstFragmentPath,
                    "--from",
                    "missing",
                    seed = requestedFragmentExists
                )

                then("it outputs that the specified loadout was not found") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout unlink is run with an empty --from value") {
                val execution by memoizedAction(
                    "unlink",
                    firstFragmentPath,
                    "--from",
                    "",
                    seed = requestedFragmentExists
                )

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the loadout named by --from is valid and not currently active") {
            val validInactiveTarget: ScenarioSeed = {
                givenValidLoadout(
                    name = "target",
                    fragments =
                    listOf(
                        firstFragmentPath to firstFragmentContent,
                        secondFragmentPath to secondFragmentContent
                    )
                )
            }

            given("the fragment is present in the target loadout") {
                action("loadout unlink is run") {
                    val execution by memoizedAction(
                        "unlink",
                        firstFragmentPath,
                        "--from",
                        "target",
                        seed = {
                            validInactiveTarget()
                            givenCurrentLoadoutIsSet(
                                name = "current",
                                fragments = listOf(secondFragmentPath to secondFragmentContent)
                            )
                        }
                    )

                    then("it removes the fragment from the loadout") {
                        execution.scenario.shouldHaveLoadoutFragments("target", listOf(secondFragmentPath))
                    }

                    then("it outputs the remaining fragment list") {
                        execution.result.shouldContainInStdout(secondFragmentPath)
                    }

                    then("it does not change the current loadout") {
                        execution.scenario.shouldHaveCurrentLoadoutName("current")
                    }
                }

                action("loadout unlink is run with a ./ prefixed path matching a stored fragment") {
                    val execution by memoizedAction(
                        "unlink",
                        "./$firstFragmentPath",
                        "--from",
                        "target",
                        seed = validInactiveTarget
                    )

                    then("it removes the fragment using the normalized path") {
                        execution.scenario.shouldHaveLoadoutFragments("target", listOf(secondFragmentPath))
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }
                }

                action("loadout unlink is run against a legacy stored path with a ./ prefix") {
                    val execution by memoizedAction(
                        "unlink",
                        firstFragmentPath,
                        "--from",
                        "target",
                        seed = {
                            seedFragment(firstFragmentPath, firstFragmentContent)
                            seedFragment(secondFragmentPath, secondFragmentContent)
                            seedLoadout(
                                name = "target",
                                fragments = listOf("./$firstFragmentPath", secondFragmentPath)
                            )
                        }
                    )

                    then("it removes the fragment even when the stored path uses the legacy prefix") {
                        execution.scenario.shouldHaveLoadoutFragments("target", listOf(secondFragmentPath))
                    }

                    then("it exits with result 0") {
                        execution.result.shouldHaveExitCode(0)
                    }
                }

                given("the fragment was the last fragment") {
                    action("loadout unlink is run") {
                        val execution by memoizedAction(
                            "unlink",
                            firstFragmentPath,
                            "--from",
                            "target",
                            seed = {
                                givenValidLoadout(name = "target")
                            }
                        )

                        then("it reports that the loadout is now empty") {
                            execution.result.shouldContainInStdout("Loadout is now empty.")
                        }
                    }
                }
            }

            given("the fragment is not present in the target loadout") {
                action("loadout unlink is run") {
                    val execution by memoizedAction(
                        "unlink",
                        secondFragmentPath,
                        "--from",
                        "target",
                        seed = {
                            givenValidLoadout(name = "target")
                        }
                    )

                    then("it outputs a fragment-not-in-loadout error") {
                        execution.result.shouldContainInOutput("is not in loadout")
                    }

                    then("it does not change the loadout definition") {
                        execution.scenario.shouldHaveLoadoutFragments("target", listOf(firstFragmentPath))
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }
                }
            }
        }

        given("the loadout named by --from is the current loadout") {
            action("loadout unlink is run") {
                val execution by memoizedAction(
                    "unlink",
                    secondFragmentPath,
                    "--from",
                    "alpha",
                    seed = {
                        givenCurrentLoadoutIsSet(
                            name = "alpha",
                            fragments =
                            listOf(
                                firstFragmentPath to firstFragmentContent,
                                secondFragmentPath to secondFragmentContent
                            )
                        )
                    }
                )

                then("it updates the current loadout definition") {
                    execution.scenario.shouldHaveLoadoutFragments("alpha", listOf(firstFragmentPath))
                }

                then("it outputs the remaining fragment list") {
                    execution.result.shouldContainInStdout(firstFragmentPath)
                }

                then("it does not rewrite generated files automatically") {
                    execution.scenario.shouldHaveGeneratedBody("$firstFragmentContent\n\n$secondFragmentContent")
                }

                then("it causes the next read-only command to warn that the current loadout is not synchronized") {
                    execution.scenario.runCommand("list").shouldHaveStaleWarning()
                }
            }
        }
    }
})
