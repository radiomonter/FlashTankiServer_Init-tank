package flashtanki.server.discord.jda.commands.Owner

import flashtanki.server.Server
import flashtanki.server.discord.jda.commands.Permissions
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.koin.core.component.KoinComponent
import java.util.*

class StopCommand : ListenerAdapter(), KoinComponent {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message

        if (message.contentRaw.lowercase().startsWith("en?stop")) {
            val content = event.message.contentRaw.trim().lowercase(Locale.getDefault())
            if (!content.startsWith("en?stop")) {
                return
            }

            val member = event.member
            if (member == null || !Permissions.hasPermission(member)) {
                Permissions.sendNoPermissionMessage(event, "en")
                return
            }

            runBlocking {
                Server().scheduleServerStop()
            }
        }

        if (message.contentRaw.lowercase().startsWith("ru?stop")) {
            val content = event.message.contentRaw.trim().lowercase(Locale.getDefault())
            if (!content.startsWith("ru?stop")) {
                return
            }

            val member = event.member
            if (member == null || !Permissions.hasPermission(member)) {
                Permissions.sendNoPermissionMessage(event, "ru")
                return
            }

            runBlocking {
                Server().scheduleServerStop()
            }
        }
    }
}
