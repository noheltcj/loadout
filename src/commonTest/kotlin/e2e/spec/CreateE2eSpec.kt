@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.action
import e2e.support.firstFragmentContent
import e2e.support.firstFragmentPath
import e2e.support.givenSourceLoadoutExists
import e2e.support.givenValidLoadout
import e2e.support.secondFragmentContent
import e2e.support.secondFragmentPath
import e2e.support.shouldContainInOutput
import e2e.support.shouldContainInStdout
import e2e.support.shouldContainLoadoutFragmentsInOrder
import e2e.support.shouldHaveExitCode
import e2e.support.shouldHaveLoadoutFragments
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CreateE2eSpec : E2eBehaviorSuite({
    context("loadout create spec") {
        given("the requested loadout name is valid") {
            given("the requested loadout name does not already exist") {
                action("loadout create is run with no --fragment values") {
                    val execution by memoizedAction("create", "alpha")

                    then("it creates the loadout definition") {
                        execution.scenario.readLoadout("alpha").shouldNotBeNull()
                    }

                    then("it creates an empty loadout") {
                        execution.scenario.readLoadout("alpha")?.fragments shouldBe emptyList()
                    }

                    then("it outputs that the loadout was created") {
                        execution.result.shouldContainInStdout("Created loadout 'alpha'")
                    }
                }

                action("loadout create is run with --desc") {
                    val execution by memoizedAction("create", "alpha", "--desc", "Primary loadout")

                    then("it creates the loadout definition") {
                        execution.scenario.readLoadout("alpha").shouldNotBeNull()
                    }

                    then("it persists the description") {
                        execution.scenario.readLoadout("alpha")?.description shouldBe "Primary loadout"
                    }

                    then("it outputs that the loadout was created") {
                        execution.result.shouldContainInStdout("Created loadout 'alpha'")
                    }

                    then("it outputs the description") {
                        execution.result.shouldContainInStdout("Description: Primary loadout")
                    }
                }

                action("loadout create is run with one or more --fragment values") {
                    val execution by memoizedAction(
                        "create",
                        "alpha",
                        "--fragment",
                        firstFragmentPath,
                        "--fragment",
                        secondFragmentPath,
                        seed = {
                            seedFragment(firstFragmentPath, firstFragmentContent)
                            seedFragment(secondFragmentPath, secondFragmentContent)
                        }
                    )

                    then("it creates the loadout definition") {
                        execution.scenario.readLoadout("alpha").shouldNotBeNull()
                    }

                    then("it persists the fragments in the given order") {
                        execution.scenario.shouldHaveLoadoutFragments(
                            "alpha",
                            listOf(firstFragmentPath, secondFragmentPath)
                        )
                    }

                    then("it outputs that the loadout was created") {
                        execution.result.shouldContainInStdout("Created loadout 'alpha'")
                    }

                    then("it lists the fragments in success output") {
                        execution.result.shouldContainInStdout(firstFragmentPath, secondFragmentPath)
                    }
                }

                given("the requested clone source loadout exists") {
                    action("loadout create is run with --clone and no new --desc") {
                        val execution by memoizedAction(
                            "create",
                            "copy",
                            "--clone",
                            "source",
                            seed = {
                                givenSourceLoadoutExists()
                            }
                        )

                        then("it creates the loadout definition") {
                            execution.scenario.readLoadout("copy").shouldNotBeNull()
                        }

                        then("it copies the source loadout fragments") {
                            execution.scenario.shouldHaveLoadoutFragments("copy", listOf(firstFragmentPath))
                        }

                        then("it reuses the source description") {
                            execution.scenario.readLoadout("copy")?.description shouldBe "Source loadout"
                        }

                        then("it outputs that the loadout was created") {
                            execution.result.shouldContainInStdout("Created loadout 'copy' (cloned from 'source')")
                        }
                    }

                    action("loadout create is run with --clone and a new --desc") {
                        val execution by memoizedAction(
                            "create",
                            "copy",
                            "--clone",
                            "source",
                            "--desc",
                            "Copied loadout",
                            seed = {
                                givenSourceLoadoutExists(description = "Source loadout")
                            }
                        )

                        then("it creates the loadout definition") {
                            execution.scenario.readLoadout("copy").shouldNotBeNull()
                        }

                        then("it copies the source loadout fragments") {
                            execution.scenario.shouldHaveLoadoutFragments("copy", listOf(firstFragmentPath))
                        }

                        then("it overrides the source description with the new description") {
                            execution.scenario.readLoadout("copy")?.description shouldBe "Copied loadout"
                        }

                        then("it outputs that the loadout was created") {
                            execution.result.shouldContainInStdout("Created loadout 'copy' (cloned from 'source')")
                        }
                    }

                    action("loadout create is run with --clone and additional --fragment values") {
                        val execution by memoizedAction(
                            "create",
                            "copy",
                            "--clone",
                            "source",
                            "--fragment",
                            secondFragmentPath,
                            seed = {
                                givenSourceLoadoutExists()
                                seedFragment(secondFragmentPath, secondFragmentContent)
                            }
                        )

                        then("it creates the loadout definition") {
                            execution.scenario.readLoadout("copy").shouldNotBeNull()
                        }

                        then("it copies the source loadout fragments") {
                            execution.scenario
                                .readLoadout("copy")
                                ?.fragments
                                .orEmpty()
                                .shouldContain(firstFragmentPath)
                        }

                        then("it appends the additional fragments after the cloned fragments") {
                            execution.scenario.shouldHaveLoadoutFragments(
                                "copy",
                                listOf(firstFragmentPath, secondFragmentPath)
                            )
                        }

                        then("it outputs that the loadout was created") {
                            execution.result.shouldContainInStdout("Created loadout 'copy' (cloned from 'source')")
                        }
                    }
                }

                given("the requested clone source loadout does not exist") {
                    action("loadout create is run with --clone") {
                        val execution by memoizedAction("create", "copy", "--clone", "missing")

                        then("it outputs that the source loadout was not found") {
                            execution.result.shouldContainInOutput("Loadout 'missing' not found")
                        }

                        then("it exits with result 1") {
                            execution.result.shouldHaveExitCode(1)
                        }
                    }
                }

                given("one of the requested fragment paths does not exist") {
                    action("loadout create is run with one or more --fragment values") {
                        val execution by memoizedAction("create", "alpha", "--fragment", firstFragmentPath)

                        then("it outputs a fragment-not-found error") {
                            execution.result.shouldContainInOutput("Fragment not found")
                        }

                        then("it does not create the loadout definition") {
                            execution.scenario.readLoadout("alpha").shouldBeNull()
                        }

                        then("it exits with result 1") {
                            execution.result.shouldHaveExitCode(1)
                        }
                    }
                }
            }
        }

        given("the requested loadout name already exists") {
            action("loadout create is run") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    seed = {
                        givenValidLoadout(name = "alpha", description = "Original")
                    }
                )

                then("it outputs a loadout-already-exists error") {
                    execution.result.shouldContainInOutput("Loadout 'alpha' already exists")
                }

                then("it does not change the existing loadout definition") {
                    execution.scenario.readLoadout("alpha")?.description shouldBe "Original"
                    execution.scenario.shouldContainLoadoutFragmentsInOrder("alpha", listOf(firstFragmentPath))
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("the requested loadout name is invalid") {
            action("loadout create is run") {
                val execution by memoizedAction("create", "bad name")

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it does not create the loadout definition") {
                    execution.scenario.readLoadout("bad name").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }
    }
})
