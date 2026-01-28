package flashtanki.server.discord.jda.commands.Default

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color
import java.util.*
import flashtanki.server.discord.jda.commands.Permissions

class HelpCommand : ListenerAdapter() {
    private val englishCommands: MutableMap<String, String> = HashMap()
    private val russianCommands: MutableMap<String, String> = HashMap()

    init {
        englishCommands["link"] = "Gives a link to the discord server."
        englishCommands["en?online"] = "Displays the number of online players and their details."
        englishCommands["en?help"] = "Displays this help message."

        russianCommands["ссылка"] = "Дает ссылку на дискорд сервер."
        russianCommands["ru?online"] = "Отображает количество онлайн игроков и их данные."
        russianCommands["ru?help"] = "Отображает это сообщение справки."
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        val member = event.member
        if (member != null && Permissions.hasPermission(member)) {
            return
        }

        val content = event.message.contentRaw.trim().lowercase(Locale.getDefault())

        if (content.startsWith("en?help")) {
            sendHelpMessage(event, "English")
        } else if (content.startsWith("ru?help")) {
            sendHelpMessage(event, "Russian")
        }
    }

    private fun sendHelpMessage(event: MessageReceivedEvent, language: String) {
        val commands: Map<String, String> =
            if (language.equals("English", ignoreCase = true)) englishCommands else russianCommands
        val embedBuilder = EmbedBuilder()
        val title = if (language.equals("English", ignoreCase = true)) "Command List" else "Список Команд"
        embedBuilder.setTitle(title)
            .setColor(Color.GREEN)

        for ((key, value) in commands) {
            embedBuilder.addField(key, value, false)
        }

        val embed = embedBuilder.build()
        event.channel.sendMessage(embed).queue()
    }
}
