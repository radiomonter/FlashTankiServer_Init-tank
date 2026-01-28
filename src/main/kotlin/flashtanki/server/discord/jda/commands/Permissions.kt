package flashtanki.server.discord.jda.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import java.awt.Color

object Permissions {
    private const val ALLOWED_ROLE_ID = "1258403175770099762"

    fun hasPermission(member: Member): Boolean {
        return member.roles.any { it.id == ALLOWED_ROLE_ID }
    }

    fun sendNoPermissionMessage(event: MessageReceivedEvent, language: String) {
        val embedBuilder = EmbedBuilder()
        val title = if (language.equals("en", ignoreCase = true)) "Permission Error" else "Ошибка доступа"
        val message = if (language.equals("en", ignoreCase = true)) "You do not have permission to use this command." else "У вас нет прав на использование этой команды."
        embedBuilder.setTitle(title)
            .setDescription(message)
            .setColor(Color.RED)

        val embed = embedBuilder.build()
        event.channel.sendMessage(embed).queue()
    }
}
