package flashtanki.server.discord.jda.commands.Owner

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.awt.Color
import java.util.*
import flashtanki.server.discord.jda.commands.Permissions

class HelpCommands : ListenerAdapter() {
    private val englishCommands: MutableMap<String, String> = HashMap()
    private val russianCommands: MutableMap<String, String> = HashMap()

    init {
        englishCommands["en?stop"] = "Stops the server."
        englishCommands["en?online"] = "Displays the number of online players and their details."
        englishCommands["en?invite toggle"] = "Toggles the invite code requirement on or off."
        englishCommands["en?invite add <code>"] = "Adds a new invite code."
        englishCommands["en?invite delete <code>"] = "Deletes an invite code."
        englishCommands["en?invite list"] = "Lists all invite codes."
        englishCommands["en?invite give"] = "Generates an invite code for a mentioned user."
        englishCommands["en?help"] = "Displays this help message."
        englishCommands["link"] = "Gives a link to the discord server."

        russianCommands["ru?stop"] = "Останавливает сервер."
        russianCommands["ru?online"] = "Отображает количество онлайн игроков и их данные."
        russianCommands["ru?invite toggle"] = "Включает или выключает требование кода приглашения."
        russianCommands["ru?invite add <code>"] = "Добавляет новый код приглашения."
        russianCommands["ru?invite delete <code>"] = "Удаляет код приглашения."
        russianCommands["ru?invite list"] = "Выводит список всех кодов приглашений."
        russianCommands["ru?invite give"] = "Генерирует код приглашения для указанного пользователя."
        russianCommands["ru?help"] = "Отображает это сообщение справки."
        russianCommands["ссылка"] = "Дает ссылку на дискорд сервер."
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        val content = event.message.contentRaw.trim().lowercase(Locale.getDefault())

        if (!content.startsWith("en?help") && !content.startsWith("ru?help")) {
            return
        }

        val member = event.member
        if (member == null || !Permissions.hasPermission(member)) {
            return
        }


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
