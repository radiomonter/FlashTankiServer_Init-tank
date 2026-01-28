package flashtanki.server.commands.handlers

import kotlinx.coroutines.launch
import mu.KotlinLogging
import flashtanki.server.client.SocketLocale
import flashtanki.server.client.UserSocket
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler

class SystemHandler : ICommandHandler {
  private val logger = KotlinLogging.logger { }

  @CommandHandler(CommandName.GetAesData)
  suspend fun getAesData(socket: UserSocket, localeId: String) {
    logger.debug { "Initialized client locale: $localeId" }

    socket.locale = SocketLocale.get(localeId)

    // ClientDependency.await() can deadlock execution if suspended
    socket.coroutineScope.launch { socket.initClient() }
  }

  @CommandHandler(CommandName.Error)
  suspend fun error(socket: UserSocket, error: String) {
    logger.warn { "Client-side error occurred: $error" }
  }

  @CommandHandler(CommandName.DependenciesLoaded)
  suspend fun dependenciesLoaded(socket: UserSocket, id: Int) {
    val dependency = socket.dependencies[id] ?: throw IllegalStateException("Dependency $id not found")
    dependency.loaded()
  }
}
