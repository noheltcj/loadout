@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.entity.UserSettings
import domain.entity.UserSettingsFile
import domain.entity.UserSettingsLocation
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.policy.UserSettingsRepair
import domain.policy.UserSettingsRepairWarning
import domain.policy.repairUserSettings
import domain.policy.repairUserSettingsForRepository
import domain.policy.validateUserSettings
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

private val userSettingsLocation = UserSettingsLocation(
    path = "/home/developer/.config/loadout/config.json",
    pathInHome = null
)

private fun <T> Result<T, LoadoutError>.accepted(): T = (this as Result.Success).value

private fun <T> Result<T, LoadoutError>.rejection(): LoadoutError.ValidationError =
    (this as Result.Error).error as LoadoutError.ValidationError

private fun UserSettingsRepair.ignoredOutputFiles(): List<String> =
    warnings
        .filterIsInstance<UserSettingsRepairWarning.InvalidOutputFileIgnored>()
        .map(UserSettingsRepairWarning.InvalidOutputFileIgnored::invalidOutputFile)

class UserSettingsValidationSpec : BehaviorSpec({
    Given("user settings list a file that some repository may keep in its fragments home") {
        val settings = UserSettings(outputFiles = listOf("CLAUDE.md", "fragments/notes.md"))

        When("the user settings are validated") {
            val validation = validateUserSettings(settings, loadoutFileLocations)

            Then("it accepts the list, because each repository checks its own fragments home") {
                validation.accepted() shouldBe settings
            }
        }

        Given("the settings file holds them") {
            val file = UserSettingsFile.Present(userSettingsLocation, settings)

            When("the user settings are repaired for a repository whose fragments home is fragments") {
                val repair = repairUserSettingsForRepository(file, loadoutFileLocations, fragmentsHome = "fragments")

                Then("it keeps only the files outside that fragments home") {
                    repair.settings.outputFiles.shouldContainExactly("CLAUDE.md")
                }
                Then("it reports the ignored file") {
                    repair.ignoredOutputFiles().shouldContainExactly("fragments/notes.md")
                }
            }

            When("the user settings are repaired outside any repository") {
                val repair = repairUserSettings(file, loadoutFileLocations)

                Then("it keeps every file") {
                    repair.settings shouldBe settings
                }
            }
        }
    }

    mapOf(
        "user settings list a file inside the git directory" to listOf(".git/notes.md"),
        "user settings list two files that differ only by case" to listOf("CLAUDE.md", "claude.md"),
        "user settings list no files" to emptyList(),
    ).forEach { (description, outputFiles) ->
        Given(description) {
            val settings = UserSettings(outputFiles = outputFiles)

            When("the user settings are validated") {
                val validation = validateUserSettings(settings, loadoutFileLocations)

                Then("it rejects the output files setting") {
                    validation.rejection().field shouldBe "output-files"
                }
            }
        }
    }

    Given("the user settings file lists only files inside the fragments home") {
        val file =
            UserSettingsFile.Present(userSettingsLocation, UserSettings(outputFiles = listOf("fragments/notes.md")))

        When("the user settings are repaired for a repository whose fragments home is fragments") {
            val repair = repairUserSettingsForRepository(file, loadoutFileLocations, fragmentsHome = "fragments")

            Then("it leaves the list unset, so the repository setting applies") {
                repair.settings.outputFiles shouldBe null
            }
        }
    }

    Given("the user settings file cannot be read") {
        val error = LoadoutError.UnreadableUserSettings(userSettingsLocation.path)
        val file = UserSettingsFile.Unreadable(userSettingsLocation, error)

        When("the user settings are repaired") {
            val repair = repairUserSettings(file, loadoutFileLocations)

            Then("it applies no user settings") {
                repair.settings shouldBe UserSettings()
            }
            Then("it warns that the file was ignored") {
                repair.warnings.shouldContainExactly(
                    UserSettingsRepairWarning.UnreadableSettingsIgnored(userSettingsLocation, error)
                )
            }
        }
    }

    Given("the user settings file does not exist") {
        val file = UserSettingsFile.Absent(userSettingsLocation)

        When("the user settings are repaired") {
            val repair = repairUserSettings(file, loadoutFileLocations)

            Then("it applies no user settings") {
                repair.settings shouldBe UserSettings()
            }
            Then("it emits no warnings") {
                repair.warnings shouldBe emptyList()
            }
        }
    }

    Given("user settings list a file that escapes the repository") {
        val settings = UserSettings(outputFiles = listOf("../outside.md"))

        When("the user settings are validated") {
            val validation = validateUserSettings(settings, loadoutFileLocations)

            Then("it explains the required path shape") {
                validation.rejection().validationMessage.shouldContain("must be a repository-relative Markdown file")
            }
        }

        Given("the settings file holds them") {
            val file = UserSettingsFile.Present(userSettingsLocation, settings)

            When("the user settings are repaired") {
                val repair = repairUserSettings(file, loadoutFileLocations)

                Then("it reports the ignored file") {
                    repair.warnings.single().shouldBeInstanceOf<UserSettingsRepairWarning.InvalidOutputFileIgnored>()
                }
            }
        }
    }
})
