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
import e2e.support.thirdFragmentContent
import e2e.support.thirdFragmentPath

class AddE2eSpec : E2eBehaviorSuite({
    context("loadout add spec") {
        given("the parser receives loadout add without --to") {
            action("loadout add is run without --to") {
                val execution by memoizedAction("add", firstFragmentPath)

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs parser guidance for the missing --to option") {
                    execution.result.shouldContainInOutput("--to")
                }
            }
        }

        given("the loadout named by --to is invalid") {
            action("loadout add is run") {
                val execution by memoizedAction(
                    "add",
                    firstFragmentPath,
                    "--to",
                    "missing",
                    seed = {
                        seedFragment(firstFragmentPath, firstFragmentContent)
                    }
                )

                then("it outputs that the specified loadout was not found") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the loadout named by --to is valid and not currently active") {
            val validInactiveTarget: ScenarioSeed = {
                givenValidLoadout(name = "target")
            }

            given("the specified fragment path does not exist") {
                action("loadout add is run") {
                    val execution by memoizedAction(
                        "add",
                        secondFragmentPath,
                        "--to",
                        "target",
                        seed = validInactiveTarget
                    )

                    then("it outputs a fragment-not-found error") {
                        execution.result.shouldContainInOutput("Fragment not found")
                    }

                    then("it does not change the loadout definition") {
                        execution.scenario.shouldHaveLoadoutFragments("target", listOf(firstFragmentPath))
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }
                }
            }

            given("the fragment is not already in the target loadout") {
                action("loadout add is run without --after") {
                    val execution by memoizedAction(
                        "add",
                        thirdFragmentPath,
                        "--to",
                        "target",
                        seed = {
                            givenValidLoadout(name = "target")
                            givenCurrentLoadoutIsSet(
                                name = "current",
                                fragments = listOf(secondFragmentPath to secondFragmentContent)
                            )
                            seedFragment(thirdFragmentPath, thirdFragmentContent)
                        }
                    )

                    then("it appends the fragment to the end of the loadout") {
                        execution.scenario.shouldHaveLoadoutFragments(
                            "target",
                            listOf(firstFragmentPath, thirdFragmentPath)
                        )
                    }

                    then("it outputs the updated fragment list") {
                        execution.result.shouldContainInStdout(thirdFragmentPath)
                    }

                    then("it does not change the current loadout") {
                        execution.scenario.shouldHaveCurrentLoadoutName("current")
                    }
                }

                action("loadout add is run with --after referencing an existing fragment") {
                    val execution by memoizedAction(
                        "add",
                        thirdFragmentPath,
                        "--to",
                        "target",
                        "--after",
                        firstFragmentPath,
                        seed = {
                            givenValidLoadout(
                                name = "target",
                                fragments =
                                    listOf(
                                        firstFragmentPath to firstFragmentContent,
                                        secondFragmentPath to secondFragmentContent
                                    )
                            )
                            seedFragment(thirdFragmentPath, thirdFragmentContent)
                        }
                    )

                    then("it inserts the new fragment immediately after that fragment") {
                        execution.scenario.shouldHaveLoadoutFragments(
                            "target",
                            listOf(firstFragmentPath, thirdFragmentPath, secondFragmentPath)
                        )
                    }

                    then("it outputs the updated fragment list") {
                        execution.result.shouldContainInStdout(thirdFragmentPath)
                    }
                }

                action("loadout add is run with --after referencing a fragment that is not in the target loadout") {
                    val execution by memoizedAction(
                        "add",
                        thirdFragmentPath,
                        "--to",
                        "target",
                        "--after",
                        "fragments/missing.md",
                        seed = {
                            givenValidLoadout(name = "target")
                            seedFragment(thirdFragmentPath, thirdFragmentContent)
                        }
                    )

                    then("it appends the new fragment to the end of the loadout instead of failing") {
                        execution.scenario.shouldHaveLoadoutFragments(
                            "target",
                            listOf(firstFragmentPath, thirdFragmentPath)
                        )
                    }

                    then("it outputs the updated fragment list") {
                        execution.result.shouldContainInStdout(thirdFragmentPath)
                    }
                }
            }

            given("the fragment is already in the target loadout") {
                action("loadout add is run") {
                    val execution by memoizedAction(
                        "add",
                        firstFragmentPath,
                        "--to",
                        "target",
                        seed = validInactiveTarget
                    )

                    then("it outputs a duplicate fragment error") {
                        execution.result.shouldContainInOutput("already in loadout")
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

        given("the loadout named by --to is the current loadout") {
            action("loadout add is run") {
                val execution by memoizedAction(
                    "add",
                    secondFragmentPath,
                    "--to",
                    "alpha",
                    seed = {
                        givenCurrentLoadoutIsSet(name = "alpha")
                        seedFragment(secondFragmentPath, secondFragmentContent)
                    }
                )

                then("it updates the current loadout definition") {
                    execution.scenario.shouldHaveLoadoutFragments(
                        "alpha",
                        listOf(firstFragmentPath, secondFragmentPath)
                    )
                }

                then("it outputs the updated fragment list") {
                    execution.result.shouldContainInStdout(secondFragmentPath)
                }

                then("it does not rewrite generated files automatically") {
                    execution.scenario.shouldHaveGeneratedBody(firstFragmentContent)
                }

                then("it causes the next read-only command to warn that the current loadout is not synchronized") {
                    execution.scenario.runCommand("list").shouldHaveStaleWarning()
                }
            }
        }
    }
})
