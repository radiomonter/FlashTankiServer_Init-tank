package flashtanki.server.discord.jda.commands.Moderator

import flashtanki.server.discord.jda.commands.Permissions
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color
import java.util.*
import java.util.concurrent.TimeUnit

class ClearChatCommand : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        val content = event.message.contentRaw.trim().lowercase(Locale.getDefault())

        when {
            content.startsWith("en?clear") -> {
                val args = content.removePrefix("en?clear").trim().split("\\s+".toRegex())
                val channel = event.textChannel

                if (!content.startsWith("en?clear")) {
                    return
                }

                val member = event.member ?: return
                if (!Permissions.hasPermission(member)) {
                    Permissions.sendNoPermissionMessage(event, "en")
                    return
                }

                if (args.size != 1 || !args[0].matches("\\d+".toRegex())) {
                    val embed = EmbedBuilder()
                        .setTitle("Invalid usage")
                        .setDescription("Correct usage: `en?clear <number>`")
                        .setColor(Color.RED)
                    event.channel.sendMessage(embed.build()).queue()
                    return
                }

                val count = args[0].toInt()
                if (count <= 0 || count > 100) {
                    val embed = EmbedBuilder()
                        .setTitle("Invalid count")
                        .setDescription("You can only delete between 1 and 100 messages at once.")
                        .setColor(Color.RED)
                    event.channel.sendMessage(embed.build()).queue()
                    return
                }

                channel.history.retrievePast(count).queue { messages ->
                    channel.purgeMessages(messages)
                    val embed = EmbedBuilder()
                        .setTitle("Messages Deleted")
                        .setDescription("Deleted $count messages.")
                        .setColor(Color.YELLOW)
                    event.channel.sendMessage(embed.build()).queue { response ->
                        response.delete().queueAfter(5, TimeUnit.SECONDS)
                    }
                }
            }
            content.startsWith("ru?clear") -> {
                val args = content.removePrefix("ru?clear").trim().split("\\s+".toRegex())
                val channel = event.textChannel

                if (!content.startsWith("ru?clear")) {
                    return
                }

                val member = event.member ?: return
                if (!Permissions.hasPermission(member)) {
                    Permissions.sendNoPermissionMessage(event, "ru")
                    return
                }

                if (args.size != 1 || !args[0].matches("\\d+".toRegex())) {
                    val embed = EmbedBuilder()
                        .setTitle("Недопустимое использование")
                        .setDescription("Правильное использование: `ru?clear <number>`")
                        .setColor(Color.RED)
                    event.channel.sendMessage(embed.build()).queue()
                    return
                }

                val count = args[0].toInt()
                if (count <= 0 || count > 100) {
                    val embed = EmbedBuilder()
                        .setTitle("Недопустимое количество")
                        .setDescription("Одновременно можно удалить от 1 до 100 сообщений.")
                        .setColor(Color.RED)
                    event.channel.sendMessage(embed.build()).queue()
                    return
                }

                channel.history.retrievePast(count).queue { messages ->
                    channel.purgeMessages(messages)
                    val embed = EmbedBuilder()
                        .setTitle("Сообщения удалены")
                        .setDescription("Удалено $count сообщений.")
                        .setColor(Color.YELLOW)
                    event.channel.sendMessage(embed.build()).queue { response ->
                        response.delete().queueAfter(5, TimeUnit.SECONDS)
                    }
                }
            }
        }
    }
}
