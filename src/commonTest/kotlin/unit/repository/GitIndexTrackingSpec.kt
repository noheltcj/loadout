@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package unit.repository

import data.repository.isTrackedInIndex
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class GitIndexTrackingSpec : BehaviorSpec({
    Given("an index that records AGENTS.md") {
        val index = gitIndex(entries = listOf("AGENTS.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe true
            }
        }

        When("the index is looked up for a path git does not record") {
            Then("it reports the path as untracked") {
                isTrackedInIndex(index, "GEMINI.md") shouldBe false
            }
        }
    }

    Given("an index that records agents.md in lowercase") {
        val index = gitIndex(entries = listOf("agents.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe true
            }
        }
    }

    Given("an index that records a nested path") {
        val index = gitIndex(entries = listOf("docs/CLAUDE.md"))

        When("the index is looked up for docs/CLAUDE.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "docs/CLAUDE.md") shouldBe true
            }
        }
    }

    Given("an index whose entries need padding to reach eight-byte alignment") {
        val index = gitIndex(entries = listOf("ABCD.md", "GEMINI.md"))

        When("the index is looked up for ABCD.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "ABCD.md") shouldBe true
            }
        }

        When("the index is looked up for the later entry GEMINI.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "GEMINI.md") shouldBe true
            }
        }
    }

    Given("an index that records CLAUDE.md, AGENTS.md, and GEMINI.md") {
        val index = gitIndex(entries = listOf("CLAUDE.md", "AGENTS.md", "GEMINI.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe true
            }
        }
    }

    Given("an empty index") {
        val index = gitIndex(entries = emptyList<String>())

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as untracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe false
            }
        }
    }

    Given("a version 3 index that records AGENTS.md") {
        val index = gitIndex(version = 3, entries = listOf("AGENTS.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe true
            }
        }
    }

    Given("a version 3 index whose flags word sets the extended bit") {
        val index = gitIndex(version = 3, entries = listOf("AGENTS.md" to EXTENDED_FLAG))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe true
            }
        }
    }

    Given("a version 2 index whose flags word sets the extended bit") {
        val index = gitIndex(entries = listOf("AGENTS.md" to EXTENDED_FLAG))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as untracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe false
            }
        }
    }

    Given("a version 4 index that records AGENTS.md") {
        val index = gitIndex(version = 4, entries = listOf("AGENTS.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as untracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe false
            }
        }
    }

    Given("a version 1 index that records AGENTS.md") {
        val index = gitIndex(version = 1, entries = listOf("AGENTS.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as tracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe true
            }
        }
    }

    Given("bytes that are not a git index") {
        val index = gitIndex(magic = "XXXX", entries = listOf("AGENTS.md"))

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as untracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe false
            }
        }
    }

    Given("an index whose header counts more entries than it holds") {
        val index = gitIndex(entries = listOf("AGENTS.md"), entryCount = 2)

        When("the index is looked up for AGENTS.md") {
            Then("it reports the path as untracked") {
                isTrackedInIndex(index, "AGENTS.md") shouldBe false
            }
        }
    }
})

private const val EXTENDED_FLAG = 0x4000
private const val FIXED_PART_SIZE = 62
private const val EXTENDED_FLAGS_SIZE = 2
private const val ENTRY_ALIGNMENT = 8
private const val TRAILER_SIZE = 20

private fun gitIndex(
    entries: List<String>,
    version: Int = 2,
    magic: String = "DIRC",
    entryCount: Int? = null,
): ByteArray = gitIndex(entries.map { it to 0 }, version, magic, entryCount)

private fun gitIndex(
    entries: List<Pair<String, Int>>,
    version: Int = 2,
    magic: String = "DIRC",
    entryCount: Int? = null,
): ByteArray {
    val bytes = ArrayList<Byte>()
    magic.forEach { c -> bytes.add(c.code.toByte()) }
    appendBigEndianInt(bytes, version)
    appendBigEndianInt(bytes, entryCount ?: entries.size)

    entries.forEach { (name, flags) ->
        repeat(FIXED_PART_SIZE - 2) { bytes.add(0) }
        appendBigEndianShort(bytes, flags)
        if ((flags and EXTENDED_FLAG) != 0) appendBigEndianShort(bytes, 0)

        val nameBytes = name.map { c -> c.code.toByte() }
        nameBytes.forEach { b -> bytes.add(b) }
        bytes.add(0)

        val extendedFlagsSize = if ((flags and EXTENDED_FLAG) != 0) EXTENDED_FLAGS_SIZE else 0
        val entrySize = FIXED_PART_SIZE + extendedFlagsSize + nameBytes.size + 1
        repeat((ENTRY_ALIGNMENT - entrySize % ENTRY_ALIGNMENT) % ENTRY_ALIGNMENT) { bytes.add(0) }
    }
    repeat(TRAILER_SIZE) { bytes.add(0) }
    return bytes.toByteArray()
}

private fun appendBigEndianInt(
    bytes: ArrayList<Byte>,
    value: Int,
) {
    bytes.add(((value ushr 24) and 0xFF).toByte())
    bytes.add(((value ushr 16) and 0xFF).toByte())
    bytes.add(((value ushr 8) and 0xFF).toByte())
    bytes.add((value and 0xFF).toByte())
}

private fun appendBigEndianShort(
    bytes: ArrayList<Byte>,
    value: Int,
) {
    bytes.add(((value ushr 8) and 0xFF).toByte())
    bytes.add((value and 0xFF).toByte())
}
