package flashtanki.server.discord.jda.commands.Owner

import flashtanki.server.discord.jda.commands.Permissions
import flashtanki.server.invite.IInviteRepository
import flashtanki.server.invite.IInviteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InviteServiceCommand : ListenerAdapter(), KoinComponent {

    private val inviteService by inject<IInviteService>()
    private val inviteRepository by inject<IInviteRepository>()

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message
        val content = message.contentRaw.lowercase()

        if (content.startsWith("en?invite")) {
            val args = content.removePrefix("en?invite").trim().split("\\s+".toRegex())
            val subcommand = args.getOrElse(0) { "" }
            val channel = event.channel

            if (!content.startsWith("en?invite")) {
                return
            }

            val member = event.member
            if (member == null || !Permissions.hasPermission(member)) {
                Permissions.sendNoPermissionMessage(event, "en")
                return
            }

            when (subcommand) {
                "toggle" -> {
                    inviteService.enabled = !inviteService.enabled
                    channel.sendMessage("Invite codes are now ${if (inviteService.enabled) "`enabled`" else "`not enabled`"} to enter the game.")
                        .queue()
                }

                "add" -> {
                    val code = args.getOrElse(1) { "" }
                    CoroutineScope(Dispatchers.IO).launch {
                        inviteRepository.createInvite(code)
                        channel.sendMessage("Invite code called: $code. Has been added.").queue()
                    }
                }

                "delete" -> {
                    val code = args.getOrElse(1) { "" }
                    CoroutineScope(Dispatchers.IO).launch {
                        val deleted = inviteRepository.deleteInvite(code)
                        val replyMessage = if (deleted) {
                            "Successfully removed invite code '$code'"
                        } else {
                            "Invite '$code' not found"
                        }
                        channel.sendMessage(replyMessage).queue()
                    }
                }

                "list" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val invites = inviteRepository.getInvites()
                        if (invites.isEmpty()) {
                            channel.sendMessage("No invite codes available").queue()
                            return@launch
                        }
                        val inviteList = invites.joinToString("\n") { invite -> " - ${invite.code} (ID: ${invite.id})" }
                        channel.sendMessage(inviteList).queue()
                    }
                }

                "give" -> {
                    val mentionedUsers = event.message.mentionedUsers
                    if (mentionedUsers.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val generatedCode = generateRandomCode(20)
                            inviteRepository.createInvite(generatedCode)

                            mentionedUsers.forEach { user ->
                                user.openPrivateChannel().queue { privateChannel ->
                                    privateChannel.sendMessage("Your Invite Code: `$generatedCode`").queue()
                                }
                                channel.sendMessage("Invite code successfully sent to `${user.name}`.").queue()
                            }
                        }
                    } else {
                        channel.sendMessage("Mention the user to send an invite.").queue()
                    }
                }

                else -> {
                    channel.sendMessage("Invalid command for 'invite'").queue()
                }
            }
        }

        if (content.startsWith("ru?invite")) {
            val args = content.removePrefix("ru?invite").trim().split("\\s+".toRegex())
            val subcommand = args.getOrElse(0) { "" }
            val channel = event.channel

            if (!content.startsWith("ru?invite")) {
                return
            }

            val member = event.member
            if (member == null || !Permissions.hasPermission(member)) {
                Permissions.sendNoPermissionMessage(event, "ru")
                return
            }

            when (subcommand) {
                "toggle" -> {
                    inviteService.enabled = !inviteService.enabled
                    channel.sendMessage("Инвайт коды теперь ${if (inviteService.enabled) "`нужны`" else "`не нужны`"} для входа в игру.")
                        .queue()
                }

                "add" -> {
                    val code = args.getOrElse(1) { "" }
                    CoroutineScope(Dispatchers.IO).launch {
                        inviteRepository.createInvite(code)
                        channel.sendMessage("Инвайт код: $code. Был добавлен.").queue()
                    }
                }

                "delete" -> {
                    val code = args.getOrElse(1) { "" }
                    CoroutineScope(Dispatchers.IO).launch {
                        val deleted = inviteRepository.deleteInvite(code)
                        val replyMessage = if (deleted) {
                            "Инвайт '$code' успешно удален."
                        } else {
                            "Инвайт '$code' не найдено"
                        }
                        channel.sendMessage(replyMessage).queue()
                    }
                }

                "list" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val invites = inviteRepository.getInvites()
                        if (invites.isEmpty()) {
                            channel.sendMessage("Нет доступных пригласительных кодов").queue()
                            return@launch
                        }
                        val inviteList = invites.joinToString("\n") { invite -> " - ${invite.code} (ID: ${invite.id})" }
                        channel.sendMessage(inviteList).queue()
                    }
                }

                "give" -> {
                    val mentionedUsers = event.message.mentionedUsers
                    if (mentionedUsers.isNotEmpty()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val generatedCode = generateRandomCode(20)
                            inviteRepository.createInvite(generatedCode)

                            mentionedUsers.forEach { user ->
                                user.openPrivateChannel().queue { privateChannel ->
                                    privateChannel.sendMessage("Твой инвайт код: `$generatedCode`").queue()
                                }
                                channel.sendMessage("Инвайт код успешно отправлен для `${user.name}`.").queue()
                            }
                        }
                    } else {
                        channel.sendMessage("Упомяните пользователя для отправки инвайта.").queue()
                    }
                }

                else -> {
                    channel.sendMessage("Недействительная команда для 'инвайт'").queue()
                }
            }
        }
    }

    private fun generateRandomCode(length: Int): String {
        val charset = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return List(length) { charset.random() }.joinToString("")
    }
}
