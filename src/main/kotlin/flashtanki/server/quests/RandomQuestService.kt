package flashtanki.server.quests

import org.koin.core.component.KoinComponent
import kotlin.random.Random
import kotlin.collections.Map
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import jakarta.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.hibernate.annotations.Parent
import org.koin.core.component.get
import flashtanki.server.BonusType
import flashtanki.server.battles.*
import flashtanki.server.client.*
import flashtanki.server.*

interface IRandomQuestService {
    fun getRandomQuest(index: Int, socket: UserSocket): ServerDailyQuest
    fun generateQuest(index: Int, user: User, type: KClass<out ServerDailyQuest>, args: Map<String, Any?> = emptyMap()): ServerDailyQuest
}

class RandomQuestService (): IRandomQuestService, KoinComponent //BY TitanoMachina(PACEQ)
{
    private val questConverter = QuestConverter()

    override fun getRandomQuest(index: Int, socket: UserSocket): ServerDailyQuest {
        val user = socket.user ?: throw Exception("No User")
        val locale = socket.locale ?: throw Exception("No Locale")
        val chance = Random.nextInt(1, 8)
        when (chance) {
            1 -> return generateQuest(index, user, DeliverFlagQuest::class)
            2 -> return generateQuest(index, user, KillEnemyQuest::class, mapOf("mode" to BattleMode.Deathmatch))
            3 -> return generateQuest(index, user, TakeBonusQuest::class, mapOf("bonus" to BonusType.Gold))
            4 -> return generateQuest(index, user, CapturePointQuest::class)
            5 -> return generateQuest(index, user, JoinBattleMapQuest::class, mapOf("map" to "map_island"))
            6 -> return generateQuest(index, user, EarnScoreOnMapQuest::class, mapOf("map" to "map_island"))
            7 -> return generateQuest(index, user, EarnScoreInModeQuest::class, mapOf("mode" to BattleMode.Deathmatch))
            8 -> return generateQuest(index, user, EarnScoreQuest::class)
        }
        return generateQuest(index, user, DeliverFlagQuest::class)
    }

    override fun generateQuest(index: Int, user: User, type: KClass<out ServerDailyQuest>, args: Map<String, Any?>) : ServerDailyQuest {
        fun getParameter(name: String) = type.primaryConstructor!!.parameters.single { it.name == name }

        val quest = type.primaryConstructor!!.callBy(mapOf(
                getParameter("id") to 0,
                getParameter("user") to user,
                getParameter("questIndex") to index,
                getParameter("current") to 0,
                getParameter("required") to 2,
                getParameter("new") to true,
                getParameter("completed") to false,
                getParameter("rewards") to mutableListOf<ServerDailyQuestReward>()
        ) + args.mapKeys { (name) -> getParameter(name) })
        quest.rewards += listOf(
                ServerDailyQuestReward(quest, 0, type = ServerDailyRewardType.Crystals, count = Random.nextInt(650, 1650)),
                ServerDailyQuestReward(quest, 1, type = ServerDailyRewardType.Premium, count = Random.nextInt(1, 3))
        )
        return quest
    }
}