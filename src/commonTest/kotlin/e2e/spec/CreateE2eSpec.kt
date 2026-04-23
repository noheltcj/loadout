@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package e2e.spec

import e2e.support.E2eBehaviorSuite
import e2e.support.ScenarioSeed
import e2e.support.action
import e2e.support.andThen
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
        given("a workspace with no loadouts") {
            val workspaceWithNoLoadouts: ScenarioSeed = {}
            val workspaceWithFirstFragment: ScenarioSeed =
                workspaceWithNoLoadouts.andThen {
                    seedFragment(firstFragmentPath, firstFragmentContent)
                }
            val workspaceWithRequestedFragments: ScenarioSeed =
                workspaceWithNoLoadouts.andThen {
                    seedFragment(firstFragmentPath, firstFragmentContent)
                    seedFragment(secondFragmentPath, secondFragmentContent)
                }
            val workspaceWithNonMarkdownFragment: ScenarioSeed =
                workspaceWithNoLoadouts.andThen {
                    seedFragment("fragments/script.sh", "#!/bin/bash")
                }
            val workspaceWithBinaryFragment: ScenarioSeed =
                workspaceWithNoLoadouts.andThen {
                    seedFragment("fragments/image.png", "\u0000PNG binary content")
                }

            action("loadout create is run with no --fragment values") {
                val execution by memoizedAction("create", "alpha", seed = workspaceWithNoLoadouts)

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
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--desc",
                    "Primary loadout",
                    seed = workspaceWithNoLoadouts
                )

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

            action("loadout create is run with an empty --desc") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--desc",
                    "",
                    seed = workspaceWithNoLoadouts
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it does not create the loadout definition") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
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
                    seed = workspaceWithRequestedFragments
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

            action("loadout create is run with duplicate --fragment values") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--fragment",
                    firstFragmentPath,
                    "--fragment",
                    firstFragmentPath,
                    seed = workspaceWithFirstFragment
                )

                then("it outputs a validation error") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it does not create the loadout") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout create is run with a non-markdown --fragment") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--fragment",
                    "fragments/script.sh",
                    seed = workspaceWithNonMarkdownFragment
                )

                then("it outputs a validation error about the markdown extension") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it does not create the loadout") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout create is run with a binary file as a --fragment") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--fragment",
                    "fragments/image.png",
                    seed = workspaceWithBinaryFragment
                )

                then("it outputs a validation error about the markdown extension") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it does not create the loadout") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout create is run with semantically duplicate --fragment paths") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--fragment",
                    "./$firstFragmentPath",
                    "--fragment",
                    firstFragmentPath,
                    seed = workspaceWithFirstFragment
                )

                then("it outputs a validation error about duplicate fragments") {
                    execution.result.shouldContainInOutput("Duplicate fragments")
                }

                then("it does not create the loadout") {
                    execution.scenario.readLoadout("alpha").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout create is run with a ./ prefixed --fragment path") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--fragment",
                    "./$firstFragmentPath",
                    seed = workspaceWithFirstFragment
                )

                then("it creates the loadout definition") {
                    execution.scenario.readLoadout("alpha").shouldNotBeNull()
                }

                then("it stores the normalized fragment path") {
                    execution.scenario.shouldHaveLoadoutFragments(
                        "alpha",
                        listOf(firstFragmentPath)
                    )
                }
            }

            action("loadout create is run with --clone for a missing source loadout") {
                val execution by memoizedAction(
                    "create",
                    "copy",
                    "--clone",
                    "missing",
                    seed = workspaceWithNoLoadouts
                )

                then("it outputs that the source loadout was not found") {
                    execution.result.shouldContainInOutput("Loadout 'missing' not found")
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }

            action("loadout create is run with one or more missing --fragment values") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    "--fragment",
                    firstFragmentPath,
                    seed = workspaceWithNoLoadouts
                )

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

            action("loadout create is run with an invalid name") {
                val execution by memoizedAction("create", "bad name", seed = workspaceWithNoLoadouts)

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

            action("loadout create is run with an empty name") {
                val execution by memoizedAction("create", "", seed = workspaceWithNoLoadouts)

                then("it outputs the validation error") {
                    execution.result.shouldContainInOutput("Validation error")
                }

                then("it does not create the loadout definition") {
                    execution.scenario.readLoadout("").shouldBeNull()
                }

                then("it exits with result 1") {
                    execution.result.shouldHaveExitCode(1)
                }
            }
        }

        given("a workspace with an existing empty loadout") {
            val workspaceWithExistingEmptyLoadout: ScenarioSeed = {
                givenSourceLoadoutExists(fragments = emptyList())
            }

            action("loadout create is run with --clone from that loadout") {
                val execution by memoizedAction(
                    "create",
                    "copy",
                    "--clone",
                    "source",
                    seed = workspaceWithExistingEmptyLoadout
                )

                then("it creates the loadout definition") {
                    execution.scenario.readLoadout("copy").shouldNotBeNull()
                }

                then("it copies the empty fragment list") {
                    execution.scenario
                        .readLoadout("copy")
                        ?.fragments
                        .shouldBe(emptyList())
                }

                then("it reuses the source description") {
                    execution.scenario.readLoadout("copy")?.description shouldBe "Source loadout"
                }

                then("it outputs that the loadout was created") {
                    execution.result.shouldContainInStdout("Created loadout 'copy' (cloned from 'source')")
                }
            }
        }

        given("a workspace with an existing non-empty loadout") {
            val workspaceWithExistingNonEmptyLoadout: ScenarioSeed = {
                givenSourceLoadoutExists()
            }
            val workspaceWithExistingNonEmptyLoadoutAndSecondFragment: ScenarioSeed =
                workspaceWithExistingNonEmptyLoadout.andThen {
                    seedFragment(secondFragmentPath, secondFragmentContent)
                }
            val workspaceWithExistingRequestedLoadoutName: ScenarioSeed =
                workspaceWithExistingNonEmptyLoadout.andThen {
                    givenValidLoadout(name = "alpha", description = "Original")
                }

            action("loadout create is run with --clone and no new --desc") {
                val execution by memoizedAction(
                    "create",
                    "copy",
                    "--clone",
                    "source",
                    seed = workspaceWithExistingNonEmptyLoadout
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
                    seed = workspaceWithExistingNonEmptyLoadout
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
                    seed = workspaceWithExistingNonEmptyLoadoutAndSecondFragment
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

            action("loadout create is run with an existing name") {
                val execution by memoizedAction(
                    "create",
                    "alpha",
                    seed = workspaceWithExistingRequestedLoadoutName
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
    }
})
