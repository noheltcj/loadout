@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.digest

import data.digest.sha256Hex
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

// Test vectors from FIPS 180-4 and the NIST examples that accompany it.
class Sha256Spec : BehaviorSpec({
    Given("an empty message") {
        val message = ByteArray(0)

        When("the message is digested") {
            val digest = sha256Hex(message)

            Then("it returns the standard digest as lowercase hexadecimal") {
                digest shouldBe "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
        }
    }

    Given("a message that fits in one block") {
        val message = "abc".encodeToByteArray()

        When("the message is digested") {
            val digest = sha256Hex(message)

            Then("it returns the standard digest as lowercase hexadecimal") {
                digest shouldBe "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            }
        }
    }

    Given("a message that spans two blocks") {
        val message = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()

        When("the message is digested") {
            val digest = sha256Hex(message)

            Then("it returns the standard digest as lowercase hexadecimal") {
                digest shouldBe "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1"
            }
        }
    }
})
