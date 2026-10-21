@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.policy

import domain.policy.formatBackupTimestamp
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BackupTimestampSpec : BehaviorSpec({
    Given("the Unix epoch") {
        val epochMilliseconds = 0L

        When("the time is formatted as a backup timestamp") {
            val timestamp = formatBackupTimestamp(epochMilliseconds)

            Then("it formats the first second of 1970 in UTC") {
                timestamp shouldBe "19700101T000000Z"
            }
        }
    }

    Given("a time on a leap day") {
        val epochMilliseconds = 1_709_210_096_000L

        When("the time is formatted as a backup timestamp") {
            val timestamp = formatBackupTimestamp(epochMilliseconds)

            Then("it formats February 29") {
                timestamp shouldBe "20240229T123456Z"
            }
        }
    }

    Given("the last second of a year") {
        val epochMilliseconds = 1_767_225_599_000L

        When("the time is formatted as a backup timestamp") {
            val timestamp = formatBackupTimestamp(epochMilliseconds)

            Then("it formats December 31 at 23:59:59") {
                timestamp shouldBe "20251231T235959Z"
            }
        }

        Given("one more second passes") {
            val nextSecond = epochMilliseconds + 1_000L

            When("the time is formatted as a backup timestamp") {
                val timestamp = formatBackupTimestamp(nextSecond)

                Then("it rolls over to the first second of the next year") {
                    timestamp shouldBe "20260101T000000Z"
                }
            }
        }
    }

    Given("a time with milliseconds") {
        val epochMilliseconds = 1_792_518_125_999L

        When("the time is formatted as a backup timestamp") {
            val timestamp = formatBackupTimestamp(epochMilliseconds)

            Then("it drops the milliseconds") {
                timestamp shouldBe "20261020T174205Z"
            }
        }
    }
})
