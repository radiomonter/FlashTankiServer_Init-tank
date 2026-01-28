package flashtanki.server.battles.killstreak

import kotlin.io.path.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.IResourceManager
import flashtanki.server.client.SocketLocale

class KillStreakParser : KoinComponent {
    private val logger = KotlinLogging.logger { }
    private val json by inject<Moshi>()
    private val resourceManager by inject<IResourceManager>()

    fun parse(locale: SocketLocale?): List<KillStreak> {
        val fileName = when (locale) {
            SocketLocale.Russian -> "battle_resources/killstreaks_ru.json"
            SocketLocale.English -> "battle_resources/killstreaks_en.json"
            else -> "battle_resources/killstreaks_en.json"
        }

        val jsonValue = resourceManager.get(fileName)

        val adapt: JsonAdapter<List<KillStreak>> = json.adapter(
            Types.newParameterizedType(List::class.java, KillStreak::class.java)
        )

        return adapt.fromJson(jsonValue.readText())
            ?: emptyList()
    }
}
