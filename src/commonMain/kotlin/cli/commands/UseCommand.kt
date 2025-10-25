package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.help
import domain.service.LoadoutService
import domain.entity.packaging.Result
import domain.entity.ComposedOutput
import domain.service.LoadoutCompositionService
import domain.repository.FileRepository

class UseCommand(
    private val fileRepository: FileRepository,
    private val loadoutService: LoadoutService,
    private val composeLoadout: LoadoutCompositionService
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

    override fun run() {
        when (val result = loadoutService.getLoadout(name)) {
            is Result.Success -> {
                val loadout = result.value
                
                when (val composeResult = composeLoadout(loadout)) {
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
                            when (val setCurrentResult = loadoutService.setCurrentLoadout(name)) {
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
    
    private fun writeComposedFiles(composedOutput: ComposedOutput, outputDir: String) {
        val content = composedOutput.toFileContent(includeMetadata = true)
        
        val claudePath = "$outputDir/CLAUDE.md"
        val agentsPath = "$outputDir/AGENTS.md"
        // TODO: Directly translate fragments to cursor rules
        
        when (val claudeResult = fileRepository.writeFile(claudePath, content)) {
            is Result.Success -> Unit
            is Result.Error -> {
                echo("Warning: Failed to write CLAUDE.md: ${claudeResult.error.message}", err = true)
            }
        }
        
        when (val agentsResult = fileRepository.writeFile(agentsPath, content)) {
            is Result.Success -> Unit
            is Result.Error -> {
                echo("Warning: Failed to write AGENTS.md: ${agentsResult.error.message}", err = true)
            }
        }
    }
}