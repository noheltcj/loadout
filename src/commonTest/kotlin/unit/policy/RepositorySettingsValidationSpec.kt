@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.entity.RepositorySettings
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.validateFragmentSourceHost
import domain.policy.validateFragmentsHome
import domain.policy.validateMaximumFragmentCopyBytes
import domain.policy.validateRepositorySettings
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecWhenContainerScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun <T> Result<T, LoadoutError>.accepted(): T = (this as Result.Success).value

private fun <T> Result<T, LoadoutError>.rejection(): LoadoutError.ValidationError =
    (this as Result.Error).error as LoadoutError.ValidationError

private suspend fun <T> BehaviorSpecWhenContainerScope.thenSettingIsRejected(
    validation: Result<T, LoadoutError>,
    field: String,
    reason: String,
) {
    Then("it identifies the invalid setting") {
        validation.rejection().field shouldBe field
    }
    Then("it explains the setting constraint") {
        validation.rejection().validationMessage.shouldContain(reason)
    }
}

private suspend fun BehaviorSpecWhenContainerScope.thenSettingsAreValidated(
    validation: Result<RepositorySettings, LoadoutError>,
    expectedSettings: RepositorySettings,
) {
    Then("it preserves the configured default loadout") {
        validation.accepted().defaultLoadoutName shouldBe expectedSettings.defaultLoadoutName
    }
    Then("it returns the normalized fragments home") {
        validation.accepted().fragmentsHome shouldBe expectedSettings.fragmentsHome
    }
    Then("it returns normalized, sorted, distinct trusted hosts") {
        validation.accepted().trustedFragmentSourceHosts.shouldContainExactly(
            expectedSettings.trustedFragmentSourceHosts
        )
    }
    Then("it preserves the configured copy limit") {
        validation.accepted().maximumFragmentCopyBytes shouldBe expectedSettings.maximumFragmentCopyBytes
    }
}

class RepositorySettingsValidationSpec : BehaviorSpec({
    Given("a repository-relative fragments home") {
        val fragmentsHome = "guidance/library"

        When("the fragments home is validated") {
            val validation = validateFragmentsHome(fragmentsHome)

            Then("it preserves the repository-relative directory") {
                validation.accepted().value shouldBe fragmentsHome
            }
        }

        Given("the path separators are changed to Windows separators") {
            val windowsFragmentsHome = fragmentsHome.replace('/', '\\')

            When("the fragments home is validated") {
                val validation = validateFragmentsHome(windowsFragmentsHome)

                Then("it normalizes the path separators") {
                    validation.accepted().value shouldBe fragmentsHome
                }
            }
        }
    }

    mapOf(
        "a fragments home traverses above the repository root" to "../outside",
        "a fragments home traverses through an interior segment" to "guidance/../../outside",
        "an absolute fragments home path" to "/etc/loadout",
        "a fragments home uses a Windows drive path" to "C:/loadout",
        "a blank fragments home path" to "",
    ).forEach { (description, fragmentsHome) ->
        Given(description) {
            When("the fragments home is validated") {
                val validation = validateFragmentsHome(fragmentsHome)

                thenSettingIsRejected(validation, "fragments-home", "safe repository-relative directory")
            }
        }
    }

    Given("a fragment source host uses mixed case") {
        val sourceHost = "RAW.GitHubUserContent.com"

        When("the source host is validated") {
            val validation = validateFragmentSourceHost(sourceHost)

            Then("it lowercases the hostname") {
                validation.accepted() shouldBe "raw.githubusercontent.com"
            }
        }

        Given("surrounding whitespace is added") {
            val paddedHost = "  $sourceHost  "

            When("the source host is validated") {
                val validation = validateFragmentSourceHost(paddedHost)

                Then("it trims and lowercases the hostname together") {
                    validation.accepted() shouldBe "raw.githubusercontent.com"
                }
            }
        }
    }

    mapOf(
        "a fragment source host is an IPv4 literal" to "169.254.169.254",
        "a fragment source host includes a URL scheme" to "https://raw.githubusercontent.com",
        "a fragment source host includes a port" to "raw.githubusercontent.com:443",
        "a fragment source host is a wildcard pattern" to "*.githubusercontent.com",
        "a fragment source host includes a URL path" to "raw.githubusercontent.com/owner",
        "a fragment source host has no domain" to "localhost",
        "a fragment source host is blank" to " ",
        "a fragment source host is an IPv6 literal" to "[::1]",
        "a fragment source hostname starts a label with a hyphen" to "-invalid.example.com",
    ).forEach { (description, sourceHost) ->
        Given(description) {
            When("the source host is validated") {
                val validation = validateFragmentSourceHost(sourceHost)

                thenSettingIsRejected(validation, "fragment-source-host", "exact DNS hostname")
            }
        }
    }

    Given("a maximum fragment copy size inside the supported range") {
        val maximumBytes = 1_024

        When("the maximum copy size is validated") {
            val validation = validateMaximumFragmentCopyBytes(maximumBytes)

            Then("it preserves the configured size") {
                validation.accepted() shouldBe maximumBytes
            }
        }
    }

    mapOf(
        "the maximum fragment copy size is the minimum supported value" to 1,
        "the maximum fragment copy size equals the hard ceiling" to
            RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES,
    ).forEach { (description, maximumBytes) ->
        Given(description) {
            When("the maximum copy size is validated") {
                val validation = validateMaximumFragmentCopyBytes(maximumBytes)

                Then("it accepts the boundary value") {
                    validation.accepted() shouldBe maximumBytes
                }
            }
        }
    }

    mapOf(
        "the maximum fragment copy size exceeds the ceiling by one byte" to
            RepositorySettings.DEFAULT_MAXIMUM_FRAGMENT_COPY_BYTES + 1,
        "the maximum fragment copy size is zero" to 0,
        "the maximum fragment copy size is negative" to -1,
    ).forEach { (description, maximumBytes) ->
        Given(description) {
            When("the maximum copy size is validated") {
                val validation = validateMaximumFragmentCopyBytes(maximumBytes)

                thenSettingIsRejected(validation, "maximum-fragment-bytes", "between 1 and 200000")
            }
        }
    }

    Given("repository settings use the default values") {
        val settings = RepositorySettings()

        When("the repository settings are validated") {
            val validation = validateRepositorySettings(settings)

            thenSettingsAreValidated(validation, settings)
        }
    }

    Given("repository settings configure a project loadout and a custom fragment copy policy") {
        val settings = RepositorySettings(
            defaultLoadoutName = "project",
            fragmentsHome = "guidance/library",
            trustedFragmentSourceHosts = listOf("trusted.example.com"),
            maximumFragmentCopyBytes = 1_024,
        )

        When("the repository settings are validated") {
            val validation = validateRepositorySettings(settings)

            thenSettingsAreValidated(validation, settings)
        }

        Given("the fragments home is changed to a Windows-separated path") {
            val windowsSettings = settings.copy(fragmentsHome = "guidance\\library")

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(windowsSettings)

                thenSettingsAreValidated(validation, settings)
            }
        }

        Given("mixed-case trusted hosts are added out of order") {
            val unsortedSettings = settings.copy(
                trustedFragmentSourceHosts = listOf("RAW.githubusercontent.com", "example.com")
            )
            val expectedSettings = settings.copy(
                trustedFragmentSourceHosts = listOf("example.com", "raw.githubusercontent.com")
            )

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(unsortedSettings)

                thenSettingsAreValidated(validation, expectedSettings)
            }
        }

        Given("trusted hosts that differ only by case and whitespace are added") {
            val duplicateSettings = settings.copy(
                trustedFragmentSourceHosts = listOf("example.com", " EXAMPLE.com ")
            )
            val expectedSettings = settings.copy(trustedFragmentSourceHosts = listOf("example.com"))

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(duplicateSettings)

                thenSettingsAreValidated(validation, expectedSettings)
            }
        }

        Given("multiple settings need normalization together") {
            val mixedSettings = settings.copy(
                fragmentsHome = "guidance\\library",
                trustedFragmentSourceHosts = listOf(" RAW.githubusercontent.com ", "example.com", "EXAMPLE.com"),
            )
            val expectedSettings = settings.copy(
                trustedFragmentSourceHosts = listOf("example.com", "raw.githubusercontent.com")
            )

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(mixedSettings)

                thenSettingsAreValidated(validation, expectedSettings)
            }
        }

        Given("the fragments home is changed to an escaping path") {
            val unsafeSettings = settings.copy(fragmentsHome = "../outside")

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(unsafeSettings)

                thenSettingIsRejected(validation, "fragments-home", "safe repository-relative directory")
            }
        }

        Given("an invalid host is added alongside a valid trusted host") {
            val unsafeSettings = settings.copy(
                trustedFragmentSourceHosts = settings.trustedFragmentSourceHosts + "169.254.169.254"
            )

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(unsafeSettings)

                thenSettingIsRejected(validation, "fragment-source-host", "exact DNS hostname")
            }
        }

        Given("the maximum copy size is changed to zero") {
            val unsafeSettings = settings.copy(maximumFragmentCopyBytes = 0)

            When("the repository settings are validated") {
                val validation = validateRepositorySettings(unsafeSettings)

                thenSettingIsRejected(validation, "maximum-fragment-bytes", "between 1 and 200000")
            }
        }
    }
})
