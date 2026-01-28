package flashtanki.server.discord.jda.commands.Default

import flashtanki.server.ISocketServer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import flashtanki.server.client.Screen
import kotlinx.coroutines.DelicateCoroutinesApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Suppress("OPT_IN_IS_NOT_ENABLED")
class OnlineCommand : ListenerAdapter(), KoinComponent {

    private val socketServer by inject<ISocketServer>()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onMessageReceived(event: MessageReceivedEvent) {
        val message = event.message
        val content = message.contentRaw.lowercase()

        if (content.startsWith("en?online")) {
            GlobalScope.launch {
                val playersByScreen = socketServer.players.groupBy { it.screen }
                val onlinePlayersMessage = buildString {
                    append("__**Online players**__: ${socketServer.players.size}\n")

                    fun buildScreenMessage(screen: Screen, screenName: String) {
                        val players = playersByScreen[screen]?.size ?: 0
                        append("__**Players in $screenName**__: $players\n")
                    }

                    buildScreenMessage(Screen.Battle, "battle")
                    buildScreenMessage(Screen.BattleSelect, "choosing battles")
                }

                message.channel.sendMessage(onlinePlayersMessage.trim()).queue()
            }
        }

        if (content.startsWith("ru?online")) {
            GlobalScope.launch {
                val playersByScreen = socketServer.players.groupBy { it.screen }
                val onlinePlayersMessage = buildString {
                    append("__**Онлайн игроков**__: ${socketServer.players.size}\n")

                    fun buildScreenMessage(screen: Screen, screenName: String) {
                        val players = playersByScreen[screen]?.size ?: 0
                        append("__**Игроков в $screenName**__: $players\n")
                    }

                    buildScreenMessage(Screen.Battle, "битве")
                    buildScreenMessage(Screen.BattleSelect, "выборе битв")
                }

                message.channel.sendMessage(onlinePlayersMessage.trim()).queue()
            }
        }
    }
}
