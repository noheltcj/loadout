package cli

import com.github.ajalt.clikt.core.subcommands
import cli.commands.*
import application.LoadoutService
import application.CompositionEngine
import infrastructure.*

fun main(args: Array<String>) {
    val (loadoutService, compositionEngine) = initializeDependencies()
    
    LoadoutCli(
        loadoutService = loadoutService,
        compositionEngine = compositionEngine
    )
        .subcommands(
            ListCommand(
                loadoutService = loadoutService
            ),
            CreateCommand(
                loadoutService = loadoutService
            ),
            UseCommand(
                loadoutService = loadoutService,
                compositionEngine = compositionEngine
            ),
            AddCommand(
                loadoutService = loadoutService
            ),
            RemoveCommand(
                loadoutService = loadoutService
            )
        )
        .main(args)
}

private fun initializeDependencies(): Dependencies {
    val fileSystem = PlatformFileSystem()
    val serializer = JsonSerializer()
    
    val configRepository = FileBasedConfigRepository(fileSystem, serializer)
    val loadoutRepository = FileBasedLoadoutRepository(fileSystem, serializer)
    val fragmentRepository = FileBasedFragmentRepository(fileSystem)
    
    return Dependencies(
        loadoutService = LoadoutService(loadoutRepository, configRepository),
        compositionEngine = CompositionEngine(fragmentRepository)
    )
}

private data class Dependencies(
    val loadoutService: LoadoutService,
    val compositionEngine: CompositionEngine
)