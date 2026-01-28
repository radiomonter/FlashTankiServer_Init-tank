package flashtanki.server.commands.handlers

import flashtanki.server.battles.*
import flashtanki.server.battles.mode.TeamModeHandler
import kotlin.reflect.jvm.jvmName
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.chat.CommandInvocationSource
import flashtanki.server.chat.CommandParseResult
import flashtanki.server.chat.IChatCommandRegistry
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler

class BattleChatHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val chatCommandRegistry by inject<IChatCommandRegistry>()

  @CommandHandler(CommandName.SendBattleChatMessageServer)
  suspend fun sendBattleChatMessageServer(socket: UserSocket, content: String, isTeam: Boolean) {
    val user = socket.user ?: throw Exception("No User")
    val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
    val battle = player.battle

    if(content.startsWith("/")) {
      logger.debug { "Parsing message as command: $content" }

      val args = chatCommandRegistry.parseArguments(content.drop(1))
      logger.debug { "Parsed arguments: $args" }

      when(val result = chatCommandRegistry.parseCommand(args)) {
        is CommandParseResult.Success          -> {
          logger.debug { "Parsed command: ${result.parsedCommand.command.name}" }

          try {
            chatCommandRegistry.callCommand(socket, result.parsedCommand, CommandInvocationSource.BattleChat)
          } catch(exception: Exception) {
            logger.error(exception) { "An exception occurred while calling command ${result.parsedCommand.command.name}" }

            val builder = StringBuilder()
            builder.append(exception::class.qualifiedName ?: exception::class.simpleName ?: exception::class.jvmName)
            builder.append(": ")
            builder.append(exception.message ?: exception.localizedMessage)
            builder.append("\n")
            exception.stackTrace.forEach { frame ->
              builder.appendLine("    at $frame")
            }

            socket.sendBattleChat("An exception occurred while calling command ${result.parsedCommand.command.name}\n$builder")
          }
        }

        is CommandParseResult.UnknownCommand   -> {
          logger.debug { "Unknown command: ${result.commandName}" }
          socket.sendBattleChat("Unknown command: ${result.commandName}")
        }

        is CommandParseResult.CommandQuoted    -> {
          logger.debug { "Command name cannot be quoted" }
          socket.sendBattleChat("Command name cannot be quoted")
        }

        is CommandParseResult.TooFewArguments  -> {
          val missingArguments = result.missingArguments.map { argument -> argument.name }.joinToString(", ")

          logger.debug { "Too few arguments for command '${result.command.name}'. Missing values for: $missingArguments" }
          socket.sendBattleChat("Too few arguments for command '${result.command.name}'. Missing values for: $missingArguments")
        }

        is CommandParseResult.TooManyArguments -> {
          logger.debug { "Too many arguments for command '${result.command.name}'. Expected ${result.expected.size}, got: ${result.got.size}" }
          socket.sendBattleChat("Too many arguments for command '${result.command.name}'. Expected ${result.expected.size}, got: ${result.got.size}")
        }
      }
      return
    }

    if(user.rank.value < 5 && !content.startsWith("/")){
      val message = when (socket.locale) {
        SocketLocale.Russian -> "Чат доступен, начиная со звания Мастер-Капрал."
        SocketLocale.English -> "Chat is available starting from the rank of Master-Corporal."
        else -> "Chat is available starting from the rank of Master-Corporal."
      }
      return socket.sendBattleChat(message)
    }

    val message = BattleChatMessage(
      nickname = user.username,
      rank = user.rank.value,
      message = content,
      team = when (battle.modeHandler.mode) {
        BattleMode.Deathmatch -> false
        BattleMode.Juggernaut -> false
        else -> isTeam
      },
      chat_level = user.chatModerator,
      team_type = player.team
    )

    if (player.isSpectator) {
      if (isTeam) {
        Command(CommandName.SendBattleChatSpectatorTeamMessageClient, "[${user.username}] $content")
          .sendTo(battle, SendTarget.Spectators)
      } else {
        Command(CommandName.SendBattleChatSpectatorMessageClient, content)
          .sendTo(battle)
      }
    } else {
      val command = Command(CommandName.SendBattleChatMessageClient, message.toJson())
      if (isTeam && battle.modeHandler is TeamModeHandler) {
        battle.players.users()
          .filter { it.team == player.team }
          .forEach { it.socket.send(command) }
      } else {
        command.sendTo(battle)
      }
    }
  }
}