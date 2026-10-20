@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.entity.RepositorySettings
import domain.policy.RepositorySettingsRepair
import domain.policy.RepositorySettingsRepairWarning
import domain.policy.repairRepositorySettings
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

private suspend fun BehaviorSpecWhenContainerScope.thenRepairedSettingsHaveValues(
    repair: RepositorySettingsRepair,
    expectedSettings: RepositorySettings,
) {
    Then("it preserves the configured default loadout") {
        repair.settings.defaultLoadoutName shouldBe expectedSettings.defaultLoadoutName
    }
    Then("it returns a safe fragments home") {
        repair.settings.fragmentsHome shouldBe expectedSettings.fragmentsHome
    }
    Then("it returns normalized, sorted, distinct trusted hosts") {
        repair.settings.trustedFragmentSourceHosts.shouldContainExactly(
            expectedSettings.trustedFragmentSourceHosts
        )
    }
    Then("it returns a supported copy limit") {
        repair.settings.maximumFragmentCopyBytes shouldBe expectedSettings.maximumFragmentCopyBytes
    }
}

class RepositorySettingsRepairSpec : BehaviorSpec({
    Given("repository settings configure a project loadout and a valid custom copy policy") {
        val settings = RepositorySettings(
            defaultLoadoutName = "project",
            fragmentsHome = "guidance/library",
            trustedFragmentSourceHosts = listOf("trusted.example.com"),
            maximumFragmentCopyBytes = 1_024,
        )

        When("the repository settings are repaired") {
            val repair = repairRepositorySettings(settings)

            thenRepairedSettingsHaveValues(repair, settings)

            Then("it emits no repair warnings") {
                repair.warnings shouldBe emptyList()
            }
        }

        Given("the valid values need separator and hostname normalization") {
            val mixedSettings = settings.copy(
                fragmentsHome = "guidance\\library",
                trustedFragmentSourceHosts = listOf(" RAW.githubusercontent.com ", "example.com", "EXAMPLE.com"),
            )
            val expectedSettings = settings.copy(
                trustedFragmentSourceHosts = listOf("example.com", "raw.githubusercontent.com")
            )

            When("the repository settings are repaired") {
                val repair = repairRepositorySettings(mixedSettings)

                thenRepairedSettingsHaveValues(repair, expectedSettings)

                Then("it emits no warnings for valid values that only need normalization") {
                    repair.warnings shouldBe emptyList()
                }
            }
        }

        Given("the fragments home escapes the repository") {
            val unsafeSettings = settings.copy(fragmentsHome = "../outside")
            val expectedSettings = settings.copy(fragmentsHome = RepositorySettings.DEFAULT_FRAGMENTS_HOME)

            When("the repository settings are repaired") {
                val repair = repairRepositorySettings(unsafeSettings)

                thenRepairedSettingsHaveValues(repair, expectedSettings)

                Then("it reports the replaced fragments home") {
                    repair.warnings shouldBe listOf(
                        RepositorySettingsRepairWarning.InvalidFragmentsHomeReplaced("../outside")
                    )
                }
            }
        }

        Given("invalid hosts are added alongside valid trusted hosts") {
            val unsafeSettings = settings.copy(
                trustedFragmentSourceHosts = listOf(" EXAMPLE.com ", "169.254.169.254", "*.example.com", "example.com")
            )
            val expectedSettings = settings.copy(trustedFragmentSourceHosts = listOf("example.com"))

            When("the repository settings are repaired") {
                val repair = repairRepositorySettings(unsafeSettings)

                thenRepairedSettingsHaveValues(repair, expectedSettings)

                Then("it reports each removed host") {
                    repair.warnings.shouldContainExactlyInAnyOrder(
                        RepositorySettingsRepairWarning.InvalidTrustedHostRemoved("169.254.169.254"),
                        RepositorySettingsRepairWarning.InvalidTrustedHostRemoved("*.example.com"),
                    )
                }
            }
        }

        Given("the maximum copy size is changed to zero") {
            val unsafeSettings = settings.copy(maximumFragmentCopyBytes = 0)
            val expectedSettings = settings.copy(
                maximumFragmentCopyBytes = RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES
            )

            When("the repository settings are repaired") {
                val repair = repairRepositorySettings(unsafeSettings)

                thenRepairedSettingsHaveValues(repair, expectedSettings)

                Then("it reports the replaced copy limit") {
                    repair.warnings shouldBe listOf(RepositorySettingsRepairWarning.InvalidCopyLimitReplaced(0))
                }
            }
        }

        Given("all copy policy fields contain invalid values") {
            val unsafeSettings = settings.copy(
                fragmentsHome = "../outside",
                trustedFragmentSourceHosts = listOf("169.254.169.254"),
                maximumFragmentCopyBytes = 0,
            )
            val expectedSettings = RepositorySettings(defaultLoadoutName = settings.defaultLoadoutName)

            When("the repository settings are repaired") {
                val repair = repairRepositorySettings(unsafeSettings)

                thenRepairedSettingsHaveValues(repair, expectedSettings)

                Then("it warns that the invalid fragments home was replaced") {
                    repair.warnings.shouldContain(
                        RepositorySettingsRepairWarning.InvalidFragmentsHomeReplaced("../outside")
                    )
                }
                Then("it warns that the invalid host was removed") {
                    repair.warnings.shouldContain(
                        RepositorySettingsRepairWarning.InvalidTrustedHostRemoved("169.254.169.254")
                    )
                }
                Then("it warns that the invalid copy limit was replaced") {
                    repair.warnings.shouldContain(RepositorySettingsRepairWarning.InvalidCopyLimitReplaced(0))
                }
                Then("it emits exactly one warning for each invalid field") {
                    repair.warnings.size shouldBe 3
                }
            }
        }
    }
})
