package flashtanki.server.discord.jda.commands.Default

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class DiscordLinkCommand : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message

        if (message.contentRaw.lowercase().startsWith("link")) {
            event.channel.sendMessage("https://discord.gg/tjskEJ7SFb").queue()
        }
        if (message.contentRaw.lowercase().startsWith("ссылка")) {
            event.channel.sendMessage("https://discord.gg/tjskEJ7SFb").queue()
        }
    }

}
