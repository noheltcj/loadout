package data.repository

import domain.entity.GitRepositoryLayout
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileQueryRepository
import domain.repository.GitIndexRepository
import domain.repository.GitRepositoryLayoutRepository

private const val INDEX_FILE_NAME = "index"
private const val INDEX_MAGIC = "DIRC"
private const val INDEX_HEADER_SIZE = 12
private const val INDEX_ENTRY_FIXED_SIZE = 62
private const val INDEX_ENTRY_FLAGS_OFFSET = 60
private const val INDEX_ENTRY_EXTENDED_FLAGS_SIZE = 2
private const val INDEX_ENTRY_ALIGNMENT = 8
private const val EXTENDED_FLAGS_BIT = 0x4000
private const val LINKED_WORKTREES_DIRECTORY = "worktrees"

/**
 * Parses git's own index file instead of running git, so the answer is available wherever Loadout already looks at
 * files. Anything it cannot read unambiguously reports the path as untracked, since the result only informs a
 * warning.
 */
class FileBasedGitIndexRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val gitRepositoryLayoutRepository: GitRepositoryLayoutRepository,
) : GitIndexRepository {
    override fun isTracked(path: String): Result<Boolean, LoadoutError> =
        gitRepositoryLayoutRepository
            .resolveLayout()
            .flatMap { layout ->
                val indexPath = indexPathFor(layout)

                when {
                    indexPath == null -> Result.Success(false)
                    fileQueryRepository.fileExists(indexPath) ->
                        fileQueryRepository.readFileBytes(indexPath).map { index -> isTrackedInIndex(index, path) }

                    else -> Result.Success(false)
                }
            }

    // Each worktree keeps its index in the common directory, and the main worktree keeps it at the top level.
    private fun indexPathFor(layout: GitRepositoryLayout): String? =
        when (layout) {
            is GitRepositoryLayout.MainWorktree -> "${layout.commonDirectory}/$INDEX_FILE_NAME"
            is GitRepositoryLayout.LinkedWorktree ->
                "${layout.commonDirectory}/$LINKED_WORKTREES_DIRECTORY/${layout.worktree.id}/$INDEX_FILE_NAME"

            GitRepositoryLayout.NotARepository -> null
        }
}

/**
 * Scans a git index for [path], which is worktree-relative. Names compare case-insensitively, the way Loadout
 * compares output paths. Loadout reads index versions 1 through 3 and reports anything else as untracked.
 */
internal fun isTrackedInIndex(
    index: ByteArray,
    path: String,
): Boolean {
    if (index.size < INDEX_HEADER_SIZE) return false
    if (index.decodeToString(0, INDEX_MAGIC.length) != INDEX_MAGIC) return false

    val version = index.readBigEndianInt(4)
    if (version !in 1..3) return false

    // Every entry the header promises must parse before the index can be read unambiguously. Each entry holds a
    // fixed part, a NUL-terminated path, and zero padding that brings the entry to a multiple of eight bytes.
    // Version 3 entries hold two more flag bytes when their flags word sets the extended bit.
    val nameRanges = ArrayList<Pair<Int, Int>>()
    var offset = INDEX_HEADER_SIZE
    repeat(index.readBigEndianInt(8)) {
        if (offset + INDEX_ENTRY_FIXED_SIZE > index.size) return false

        val flags = index.readBigEndianShort(offset + INDEX_ENTRY_FLAGS_OFFSET)
        val extended = (flags and EXTENDED_FLAGS_BIT) != 0
        if (version != 3 && extended) return false

        val fixedPartSize = INDEX_ENTRY_FIXED_SIZE + if (extended) INDEX_ENTRY_EXTENDED_FLAGS_SIZE else 0
        val nameStart = offset + fixedPartSize
        var nameEnd = nameStart
        while (nameEnd < index.size && index[nameEnd] != 0.toByte()) nameEnd++
        if (nameEnd == index.size) return false

        nameRanges += Pair(nameStart, nameEnd)
        val entrySize = fixedPartSize + (nameEnd - nameStart) + 1
        offset += entrySize + (INDEX_ENTRY_ALIGNMENT - entrySize % INDEX_ENTRY_ALIGNMENT) % INDEX_ENTRY_ALIGNMENT
    }
    return nameRanges.any { (nameStart, nameEnd) ->
        index.decodeToString(nameStart, nameEnd).equals(path, ignoreCase = true)
    }
}

private fun ByteArray.readBigEndianInt(offset: Int): Int {
    var value = 0
    for (index in 0 until 4) {
        value = (value shl 8) or (this[offset + index].toInt() and 0xFF)
    }
    return value
}

private fun ByteArray.readBigEndianShort(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
