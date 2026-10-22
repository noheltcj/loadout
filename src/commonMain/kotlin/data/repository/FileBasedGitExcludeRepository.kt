package data.repository

import domain.entity.GitExcludeFileUpdate
import domain.entity.GitExcludeSections
import domain.entity.GitRepositoryLayout
import domain.entity.GitWorktree
import domain.entity.RepositoryExcludeSection
import domain.entity.RepositoryMode
import domain.entity.error.LoadoutError
import domain.entity.packaging.Result
import domain.repository.FileMutationRepository
import domain.repository.FileQueryRepository
import domain.repository.GitExcludeRepository

private const val EXCLUDE_FILE = "info/exclude"
private const val SECTION_START_PREFIX = "# >>> loadout ("
private const val SECTION_START_SUFFIX = ") - managed by Loadout, do not edit"
private const val SECTION_END_PREFIX = "# <<< loadout ("
private const val SECTION_END_SUFFIX = ")"
private const val WORKTREE_LABEL_PREFIX = "worktree: "
private const val MAIN_WORKTREE_KEY = "main"
private const val LINKED_WORKTREE_KEY_PREFIX = "worktrees/"

/**
 * Lines outside Loadout's sections belong to the user and are kept as written. Sections that cannot be parsed
 * unambiguously are reported instead of rewritten, so a hand edit never costs the user a line.
 */
class FileBasedGitExcludeRepository(
    private val fileQueryRepository: FileQueryRepository,
    private val fileMutationRepository: FileMutationRepository,
) : GitExcludeRepository {
    override fun loadSections(layout: GitRepositoryLayout.Worktree): Result<GitExcludeSections, LoadoutError> =
        readExcludeFile(layout.excludeFilePath)
            .flatMap { content -> parseExcludeBlocks(layout.excludeFilePath, content) }
            .map { blocks -> blocks.toSections() }

    override fun saveSections(
        layout: GitRepositoryLayout.Worktree,
        sections: GitExcludeSections,
    ): Result<GitExcludeFileUpdate, LoadoutError> {
        val excludeFilePath = layout.excludeFilePath

        return readExcludeFile(excludeFilePath).flatMap { currentContent ->
            parseExcludeBlocks(excludeFilePath, currentContent)
                .map { blocks -> blocks.withSections(sections).render() }
                .flatMap { updatedContent ->
                    if (updatedContent == currentContent) {
                        Result.Success(GitExcludeFileUpdate.Unchanged(excludeFilePath))
                    } else {
                        writeExcludeFile(excludeFilePath, updatedContent)
                            .map { GitExcludeFileUpdate.Updated(excludeFilePath) }
                    }
                }
        }
    }

    private fun readExcludeFile(path: String): Result<String, LoadoutError> =
        if (fileQueryRepository.fileExists(path)) {
            fileQueryRepository.readFile(path)
        } else {
            Result.Success("")
        }

    // Repositories created without git's templates have no info directory yet.
    private fun writeExcludeFile(
        path: String,
        content: String,
    ): Result<Unit, LoadoutError> =
        fileMutationRepository
            .createDirectory(path.substringBeforeLast('/'))
            .flatMap { fileMutationRepository.writeFile(path, content) }
}

// Git reads the exclude file from the common directory, which every worktree shares.
private val GitRepositoryLayout.Worktree.excludeFilePath: String
    get() = "$commonDirectory/$EXCLUDE_FILE"

private sealed interface ExcludeBlock

private data class UserLines(
    val lines: List<String>,
) : ExcludeBlock

private sealed interface SectionSlot {
    data object Repository : SectionSlot

    data class Worktree(
        val worktree: GitWorktree,
    ) : SectionSlot
}

private sealed interface ManagedSection : ExcludeBlock {
    val slot: SectionSlot
    val label: String
    val patterns: List<String>

    fun withPatterns(patterns: List<String>): ManagedSection
}

private data class RepositorySection(
    val mode: RepositoryMode,
    override val patterns: List<String>,
) : ManagedSection {
    override val slot: SectionSlot
        get() = SectionSlot.Repository

    override val label: String
        get() = "repository: ${mode.labelName} mode"

    override fun withPatterns(patterns: List<String>): ManagedSection = copy(patterns = patterns)
}

private data class WorktreeSection(
    val worktree: GitWorktree,
    override val patterns: List<String>,
) : ManagedSection {
    override val slot: SectionSlot
        get() = SectionSlot.Worktree(worktree)

    override val label: String
        get() = WORKTREE_LABEL_PREFIX + worktree.key

    override fun withPatterns(patterns: List<String>): ManagedSection = copy(patterns = patterns)
}

private val RepositoryMode.labelName: String
    get() =
        when (this) {
            RepositoryMode.Shared -> "shared"
            RepositoryMode.Local -> "local"
        }

// Linked worktrees are keyed by their path under the common directory, so one named `main` stays distinct.
private val GitWorktree.key: String
    get() =
        when (this) {
            GitWorktree.Main -> MAIN_WORKTREE_KEY
            is GitWorktree.Linked -> "$LINKED_WORKTREE_KEY_PREFIX$id"
        }

private fun managedSectionForLabel(label: String): ManagedSection? =
    RepositoryMode.entries
        .map { mode -> RepositorySection(mode, patterns = emptyList()) }
        .firstOrNull { section -> section.label == label }
        ?: label
            .takeIf { it.startsWith(WORKTREE_LABEL_PREFIX) }
            ?.removePrefix(WORKTREE_LABEL_PREFIX)
            ?.let(::worktreeKeyed)
            ?.let { worktree -> WorktreeSection(worktree, patterns = emptyList()) }

private fun worktreeKeyed(key: String): GitWorktree? =
    when {
        key == MAIN_WORKTREE_KEY -> GitWorktree.Main
        key.startsWith(LINKED_WORKTREE_KEY_PREFIX) ->
            key
                .removePrefix(LINKED_WORKTREE_KEY_PREFIX)
                .takeIf { id -> id.isNotEmpty() && '/' !in id }
                ?.let(GitWorktree::Linked)
        else -> null
    }

private fun sectionStartMarker(label: String): String = "$SECTION_START_PREFIX$label$SECTION_START_SUFFIX"

private fun sectionEndMarker(label: String): String = "$SECTION_END_PREFIX$label$SECTION_END_SUFFIX"

private fun String.sectionStartLabel(): String? =
    trimEnd()
        .takeIf { line -> line.startsWith(SECTION_START_PREFIX) && line.endsWith(SECTION_START_SUFFIX) }
        ?.removeSurrounding(SECTION_START_PREFIX, SECTION_START_SUFFIX)

private fun String.sectionEndLabel(): String? =
    trimEnd()
        .takeIf { line -> line.startsWith(SECTION_END_PREFIX) && line.endsWith(SECTION_END_SUFFIX) }
        ?.removeSurrounding(SECTION_END_PREFIX, SECTION_END_SUFFIX)

private sealed interface ParseState {
    data class BetweenSections(
        val blocks: List<ExcludeBlock>,
        val userLines: List<String>,
    ) : ParseState

    data class InsideSection(
        val blocks: List<ExcludeBlock>,
        val section: ManagedSection,
        val patterns: List<String>,
    ) : ParseState

    data class Malformed(
        val problem: String,
    ) : ParseState
}

private val nothingParsed: ParseState = ParseState.BetweenSections(blocks = emptyList(), userLines = emptyList())

private fun parseExcludeBlocks(
    path: String,
    content: String,
): Result<List<ExcludeBlock>, LoadoutError> =
    content
        .excludeLines()
        .fold(nothingParsed, ParseState::next)
        .finish()
        .flatMap { blocks ->
            blocks.duplicatedSectionProblem()?.let { problem -> Result.Error(problem) } ?: Result.Success(blocks)
        }
        .mapError { problem ->
            LoadoutError.ConfigurationError(
                "Loadout's sections in $path are malformed: $problem. Remove them and run the command again."
            )
        }

private fun String.excludeLines(): List<String> = if (isEmpty()) emptyList() else removeSuffix("\n").lines()

private fun ParseState.next(line: String): ParseState =
    when (this) {
        is ParseState.BetweenSections -> nextOutsideSection(line)
        is ParseState.InsideSection -> nextInsideSection(line)
        is ParseState.Malformed -> this
    }

private fun ParseState.BetweenSections.nextOutsideSection(line: String): ParseState {
    val startLabel = line.sectionStartLabel()

    return when {
        startLabel != null ->
            managedSectionForLabel(startLabel)
                ?.let { section -> ParseState.InsideSection(blocks.withUserLines(userLines), section, emptyList()) }
                ?: ParseState.Malformed("section '$startLabel' is not one Loadout writes")

        line.sectionEndLabel() != null ->
            ParseState.Malformed("'${line.trimEnd()}' closes a section that was never opened")

        else -> copy(userLines = userLines + line)
    }
}

private fun ParseState.InsideSection.nextInsideSection(line: String): ParseState =
    when {
        line.sectionEndLabel() == section.label ->
            ParseState.BetweenSections(blocks + section.withPatterns(patterns), userLines = emptyList())

        line.sectionStartLabel() != null || line.sectionEndLabel() != null ->
            ParseState.Malformed("section '${section.label}' is not closed")

        line.isBlank() -> this

        else -> copy(patterns = patterns + line.trimEnd())
    }

private fun ParseState.finish(): Result<List<ExcludeBlock>, String> =
    when (this) {
        is ParseState.BetweenSections -> Result.Success(blocks.withUserLines(userLines))
        is ParseState.InsideSection -> Result.Error("section '${section.label}' is not closed")
        is ParseState.Malformed -> Result.Error(problem)
    }

private fun List<ExcludeBlock>.withUserLines(userLines: List<String>): List<ExcludeBlock> =
    if (userLines.isEmpty()) this else this + UserLines(userLines)

private fun List<ExcludeBlock>.duplicatedSectionProblem(): String? =
    filterIsInstance<ManagedSection>()
        .groupBy(ManagedSection::slot)
        .values
        .firstOrNull { sections -> sections.size > 1 }
        ?.let { sections -> "section '${sections.first().label}' appears more than once" }

private fun List<ExcludeBlock>.toSections(): GitExcludeSections {
    val managedSections = filterIsInstance<ManagedSection>()

    return GitExcludeSections(
        repositorySection =
        managedSections
            .filterIsInstance<RepositorySection>()
            .singleOrNull()
            ?.let { section -> RepositoryExcludeSection.Present(section.mode, section.patterns) }
            ?: RepositoryExcludeSection.Absent,
        worktreeSections =
        managedSections
            .filterIsInstance<WorktreeSection>()
            .associate { section -> section.worktree to section.patterns },
    )
}

private fun List<ExcludeBlock>.withSections(sections: GitExcludeSections): List<ExcludeBlock> {
    val wantedSections = sections.toManagedSections()
    val wantedSectionsBySlot = wantedSections.associateBy(ManagedSection::slot)
    val keptBlocks =
        mapNotNull { block ->
            when (block) {
                is UserLines -> block
                is ManagedSection -> wantedSectionsBySlot[block.slot]
            }
        }
    val keptSlots = keptBlocks.filterIsInstance<ManagedSection>().map(ManagedSection::slot).toSet()

    return keptBlocks + wantedSections.filterNot { section -> section.slot in keptSlots }
}

private fun GitExcludeSections.toManagedSections(): List<ManagedSection> {
    val worktreeExcludeSections =
        worktreeSections.map { (worktree, patterns) -> WorktreeSection(worktree, patterns) }

    return when (repositorySection) {
        RepositoryExcludeSection.Absent -> worktreeExcludeSections
        is RepositoryExcludeSection.Present ->
            worktreeExcludeSections + RepositorySection(repositorySection.mode, repositorySection.patterns)
    }
}

/** Separates blocks with one blank line, so repeated rewrites never accumulate blank lines. */
private fun List<ExcludeBlock>.render(): String =
    mapNotNull { block -> block.renderedLines().ifEmpty { null } }
        .joinToString(separator = "\n\n", postfix = "\n") { lines -> lines.joinToString("\n") }

private fun ExcludeBlock.renderedLines(): List<String> =
    when (this) {
        is UserLines -> lines.dropWhile(String::isBlank).dropLastWhile(String::isBlank)
        is ManagedSection -> listOf(sectionStartMarker(label)) + patterns + sectionEndMarker(label)
    }
