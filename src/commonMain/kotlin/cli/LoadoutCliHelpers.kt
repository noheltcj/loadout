package cli

/**
 * Constructs the list of output file paths for the given directory.
 *
 * @param outputDir The directory where output files should be written
 * @return List of full paths to output files
 */
fun outputPaths(outputDir: String = Constants.DEFAULT_OUTPUT_DIR): List<String> =
    listOf(
        "$outputDir/${Constants.CLAUDE_MD}",
        "$outputDir/${Constants.AGENTS_MD}"
    )
