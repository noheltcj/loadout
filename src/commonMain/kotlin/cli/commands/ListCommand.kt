package cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.help
import application.LoadoutService
import common.Result
import domain.LoadoutError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ListCommand(
    private val loadoutService: LoadoutService
) : CliktCommand(
    name = "list",
    help = "List all available loadouts"
) {
    
    private val json by option("--json")
        .flag(default = false)
        .help("Output as JSON")
    
    // TODO: Inherit global --config option from main CLI

    override fun run() {
        when (val result = loadoutService.getAllLoadouts()) {
            is Result.Success -> {
                val loadouts = result.value
                
                if (loadouts.isEmpty()) {
                    echo("No loadouts found. Create one with 'loadout create <name>'")
                    return
                }
                
                if (json) {
                    outputJson(loadouts.map { LoadoutSummary.from(it) })
                } else {
                    outputTable(loadouts)
                }
            }
            is Result.Error -> handleError(result.error)
        }
    }
    
    private fun outputTable(loadouts: List<domain.Loadout>) {
        echo("Available loadouts:")
        echo()
        
        loadouts.forEach { loadout ->
            echo("• ${loadout.name}")
            if (loadout.description.isNotBlank()) {
                echo("  ${loadout.description}")
            }
            echo("  Fragments: ${loadout.fragments.size}")
            echo()
        }
    }
    
    private fun outputJson(loadouts: List<LoadoutSummary>) {
        val jsonString = Json.encodeToString(LoadoutListResponse.serializer(), LoadoutListResponse(loadouts))
        echo(jsonString)
    }
    
    private fun handleError(error: LoadoutError) {
        echo("Error: ${error.message}", err = true)
        throw ProgramResult(1)
    }
}

@Serializable
private data class LoadoutSummary(
    val name: String,
    val description: String,
    val fragmentCount: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun from(loadout: domain.Loadout): LoadoutSummary = LoadoutSummary(
            name = loadout.name,
            description = loadout.description,
            fragmentCount = loadout.fragments.size,
            createdAt = loadout.metadata.createdAt,
            updatedAt = loadout.metadata.updatedAt
        )
    }
}

@Serializable
private data class LoadoutListResponse(
    val loadouts: List<LoadoutSummary>
)