package flashtanki.server.commands.handlers

import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.ISocketServer
import flashtanki.server.client.*
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.extensions.toString
import flashtanki.server.lobby.chat.ILobbyChatManager

class LobbyChatHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val lobbyChatManager by inject<ILobbyChatManager>()
  private val server by inject<ISocketServer>()

  @CommandHandler(CommandName.SendChatMessageServer)
  suspend fun sendChatMessageServer(socket: UserSocket, nameTo: String, content: String) {
    val user = socket.user ?: throw Exception("No User")
    val receiver = server.players.singleOrNull { it.user?.username == nameTo }

    if(user.rank.value < 3 && !content.startsWith("/")) {
      val message = when(socket.locale) {
        SocketLocale.Russian -> "Чат доступен, начиная со звания Ефрейтор."
        SocketLocale.English -> "Chat is available starting from the rank of Gefreiter."
        else                 -> "Chat is available starting from the rank of Gefreiter."
      }
      return socket.sendChat(message)
    }

    if(user.mutedUntil != null && !content.startsWith("/")) {
      val message = when(socket.locale) {
        SocketLocale.Russian -> "Вы заблокированы в чате${if (user.mutedUntil != Instant.DISTANT_FUTURE) " до ${user.mutedUntil!!.toString { "$dd.$MM.$yyyy $HH:$mm:$ss" }}" else ""}${if (user.muteReason != null) " по причине: ${user.muteReason}" else ""}"
        SocketLocale.English -> "You are muted in the chat${if (user.mutedUntil != Instant.DISTANT_FUTURE) " until ${user.mutedUntil!!.toString { "$dd.$MM.$yyyy $HH:$mm:$ss" }}" else ""}${if (user.muteReason != null) " due to ${user.muteReason}" else ""}"
        else                 -> "You are muted in the chat${if (user.mutedUntil != Instant.DISTANT_FUTURE) " until ${user.mutedUntil!!.toString { "$dd.$MM.$yyyy $HH:$mm:$ss" }}" else ""}${if (user.muteReason != null) " due to ${user.muteReason}" else ""}"
      }
      return socket.sendChat(message)
    }

    val message = ChatMessage(
      name = user.username,
      rang = user.rank.value,
      chatPermissions = user.chatModerator,
      message = content,
      chatPermissionsTo = receiver?.user?.chatModerator ?: ChatModeratorLevel.None,
      rangTo = receiver?.user?.rank?.value ?: 0,
      nameTo = nameTo,
      addressed = nameTo.isNotEmpty(),
            sourceUserPremium = user.hasPremium(),
            targetUserPremium = receiver?.user?.hasPremium() ?: false
    )

    lobbyChatManager.send(socket, message)
  }
}
