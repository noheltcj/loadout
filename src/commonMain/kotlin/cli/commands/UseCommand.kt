package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.help
import application.LoadoutService
import application.CompositionEngine
import common.Result
import infrastructure.PlatformFileSystem

class UseCommand(
    private val loadoutService: LoadoutService,
    private val compositionEngine: CompositionEngine
) : CliktCommand(
    name = "use",
    help = "Switch to and compose a loadout"
) {
    
    private val name by argument(help = "Name of the loadout to use")
    
    private val dryRun by option("--dry-run")
        .flag(default = false)
        .help("Preview without writing files")
    
    private val outputDir by option("--output", "-o")
        .help("Override output directory")
    
    // TODO: Inherit global --config option from main CLI
    // TODO: Inherit global --json option from main CLI
    
    override fun run() {
        when (val result = loadoutService.getLoadout(name)) {
            is Result.Success -> {
                val loadout = result.value
                
                when (val composeResult = compositionEngine.composeLoadout(loadout)) {
                    is Result.Success -> {
                        val composedOutput = composeResult.value
                        
                        if (dryRun) {
                            echo("DRY RUN: Would switch to loadout '$name'")
                            echo("Content preview (first 200 chars):")
                            echo(composedOutput.content.take(200))
                            if (composedOutput.content.length > 200) {
                                echo("... (${composedOutput.content.length - 200} more characters)")
                            }
                        } else {
                            val setCurrentResult = loadoutService.setCurrentLoadout(name)
                            when (setCurrentResult) {
                                is Result.Success -> {
                                    writeComposedFiles(composedOutput, outputDir ?: ".")
                                    echo("Switched to loadout '$name'")
                                    echo("Generated files (${composedOutput.content.length} characters):")
                                    echo("  • CLAUDE.md")
                                    echo("  • AGENTS.md")
                                }
                                is Result.Error -> {
                                    echo("Failed to set current loadout: ${setCurrentResult.error.message}", err = true)
                                    throw ProgramResult(1)
                                }
                            }
                        }
                    }
                    is Result.Error -> {
                        echo("Failed to compose loadout: ${composeResult.error.message}", err = true)
                        throw ProgramResult(1)
                    }
                }
            }
            is Result.Error -> {
                echo("Failed to get loadout: ${result.error.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
    
    private fun writeComposedFiles(composedOutput: domain.ComposedOutput, outputDir: String) {
        val fileSystem = PlatformFileSystem()
        val content = composedOutput.toFileContent(includeMetadata = true)
        
        val claudePath = "$outputDir/CLAUDE.md"
        val agentsPath = "$outputDir/AGENTS.md"
        // TODO: Directly translate fragments to cursor rules
        
        when (val claudeResult = fileSystem.writeFile(claudePath, content)) {
            is Result.Success -> Unit
            is Result.Error -> {
                echo("Warning: Failed to write CLAUDE.md: ${claudeResult.error.message}", err = true)
            }
        }
        
        when (val agentsResult = fileSystem.writeFile(agentsPath, content)) {
            is Result.Success -> Unit
            is Result.Error -> {
                echo("Warning: Failed to write AGENTS.md: ${agentsResult.error.message}", err = true)
            }
        }
    }
}