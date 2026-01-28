package flashtanki.server.discord.jda.commands.Default

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class PingCommand : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message

        if (message.contentRaw.lowercase().startsWith("en?ping")) {
            event.channel.sendMessage("Pong! `@here`").queue()
        }
        if (message.contentRaw.lowercase().startsWith("ru?ping")) {
            event.channel.sendMessage("Понг! `@here`").queue()
        }
    }

}
