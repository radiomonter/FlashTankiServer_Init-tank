package flashtanki.server.commands.handlers

import mu.KotlinLogging
import flashtanki.server.client.ShowSettingsData
import flashtanki.server.client.UserSocket
import flashtanki.server.client.send
import flashtanki.server.client.toJson
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler

class SettingsHandler : ICommandHandler {
  private val logger = KotlinLogging.logger { }

  @CommandHandler(CommandName.ShowSettings)
  suspend fun showSettings(socket: UserSocket) {
    Command(CommandName.ClientShowSettings, ShowSettingsData().toJson()).send(socket)
  }

  @CommandHandler(CommandName.CheckPasswordIsSet)
  suspend fun checkPasswordIsSet(socket: UserSocket) {
    Command(CommandName.PasswordIsSet).send(socket)
  }
}
