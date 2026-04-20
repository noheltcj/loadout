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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class LinkE2eSpec : E2eBehaviorSuite({
    context("loadout link spec") {
        given("the requested fragment path exists in the workspace") {
            val requestedFragmentExists: ScenarioSeed = {
                seedFragment(firstFragmentPath, firstFragmentContent)
            }

            action("loadout link is run without --to") {
                val execution by memoizedAction(
                    "link",
                    firstFragmentPath,
                    seed = requestedFragmentExists
                )

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }

                then("it outputs parser guidance for the missing --to option") {
                    execution.result.shouldContainInOutput("--to")
                }
            }

            action("loadout link is run with --to referencing a missing loadout") {
                val execution by memoizedAction(
                    "link",
                    firstFragmentPath,
                    "--to",
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

            action("loadout link is run with an empty --to value") {
                val execution by memoizedAction(
                    "link",
                    firstFragmentPath,
                    "--to",
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

        given("the fragment is not a markdown file") {
            action("loadout link is run with an invalid file extension") {
                val execution by memoizedAction(
                    "link",
                    "fragments/script.sh",
                    "--to",
                    "target",
                    seed = {
                        givenValidLoadout(name = "target")
                        seedFragment("fragments/script.sh", "#!/bin/bash")
                    }
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInOutput("Validation error")
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
                action("loadout link is run") {
                    val execution by memoizedAction(
                        "link",
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
                action("loadout link is run without --after") {
                    val execution by memoizedAction(
                        "link",
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

                    then("it marks the newly added fragment in the output") {
                        execution.result.stdout.shouldContain("$thirdFragmentPath ← NEW")
                    }

                    then("it does not mark existing fragments as new") {
                        execution.result.stdout.shouldNotContain("$firstFragmentPath ← NEW")
                    }

                    then("it does not change the current loadout") {
                        execution.scenario.shouldHaveCurrentLoadoutName("current")
                    }
                }

                action("loadout link is run with --after referencing an existing fragment") {
                    val execution by memoizedAction(
                        "link",
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

                    then("it marks the inserted fragment in the output") {
                        execution.result.stdout.shouldContain("$thirdFragmentPath ← NEW")
                    }

                    then("it does not mark the first pre-existing fragment as new") {
                        execution.result.stdout.shouldNotContain("$firstFragmentPath ← NEW")
                    }

                    then("it does not mark the second pre-existing fragment as new") {
                        execution.result.stdout.shouldNotContain("$secondFragmentPath ← NEW")
                    }
                }

                action("loadout link is run with --after referencing a fragment that is not in the target loadout") {
                    val execution by memoizedAction(
                        "link",
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

                action("loadout link is run with differently formatted paths but same semantic location") {
                    val execution by memoizedAction(
                        "link",
                        "./$thirdFragmentPath", // Normalized internally -> thirdFragmentPath
                        "--to",
                        "target",
                        seed = {
                            givenValidLoadout(
                                name = "target",
                                fragments = listOf(firstFragmentPath to firstFragmentContent)
                            )
                            seedFragment(thirdFragmentPath, thirdFragmentContent)
                        }
                    )

                    then("it appends the new fragment to the loadout using the normalized path") {
                        execution.scenario.shouldHaveLoadoutFragments(
                            "target",
                            listOf(firstFragmentPath, thirdFragmentPath)
                        )
                    }

                    then("it marks the newly added fragment with the normalized path in the output") {
                        execution.result.stdout.shouldContain("$thirdFragmentPath ← NEW")
                    }
                }
            }

            given("the fragment is already in the target loadout") {
                action("loadout link is run") {
                    val execution by memoizedAction(
                        "link",
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

                action("loadout link is run against a legacy stored path with a ./ prefix") {
                    val execution by memoizedAction(
                        "link",
                        firstFragmentPath,
                        "--to",
                        "target",
                        seed = {
                            seedFragment(firstFragmentPath, firstFragmentContent)
                            seedLoadout(name = "target", fragments = listOf("./$firstFragmentPath"))
                        }
                    )

                    then("it outputs a duplicate fragment error") {
                        execution.result.shouldContainInOutput("already in loadout")
                    }

                    then("it does not append a second copy of the same fragment") {
                        execution.scenario.shouldHaveLoadoutFragments("target", listOf("./$firstFragmentPath"))
                    }

                    then("it exits with result 1") {
                        execution.result.shouldHaveExitCode(1)
                    }
                }
            }
        }

        given("the loadout named by --to is the current loadout") {
            action("loadout link is run") {
                val execution by memoizedAction(
                    "link",
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
