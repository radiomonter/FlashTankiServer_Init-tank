package flashtanki.server.matchmaking

import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.ISocketServer
import flashtanki.server.battles.*
import flashtanki.server.battles.map.IMapRegistry
import flashtanki.server.battles.map.get
import flashtanki.server.battles.mode.*
import flashtanki.server.client.UserSocket
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import java.util.concurrent.ConcurrentLinkedQueue

data class MatchmakingEntry(
    val socket: UserSocket,
    val joinTime: Instant = Clock.System.now(),
    val mode: BattleMode = BattleMode.Deathmatch
)

interface IMatchmakingService {
    suspend fun addToQueue(socket: UserSocket, mode: BattleMode = BattleMode.Deathmatch)
    suspend fun removeFromQueue(socket: UserSocket)
    fun isInQueue(socket: UserSocket): Boolean
    fun getQueueSize(): Int
}

class MatchmakingService : IMatchmakingService, KoinComponent {
    private val logger = KotlinLogging.logger { }
    
    private val battleProcessor by inject<IBattleProcessor>()
    private val mapRegistry by inject<IMapRegistry>()
    private val server by inject<ISocketServer>()
    
    private val queue = ConcurrentLinkedQueue<MatchmakingEntry>()
    private val minPlayersForMatch = 2
    private val matchCheckInterval = 3.seconds
    
    private var matchmakingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startMatchmakingLoop()
    }

    private fun startMatchmakingLoop() {
        matchmakingJob = coroutineScope.launch {
            while (isActive) {
                delay(matchCheckInterval)
                tryCreateMatches()
            }
        }
    }

    override suspend fun addToQueue(socket: UserSocket, mode: BattleMode) {
        val user = socket.user ?: return
        
        if (isInQueue(socket)) {
            logger.debug { "Player ${user.username} already in matchmaking queue" }
            return
        }

        queue.add(MatchmakingEntry(socket, mode = mode))
        logger.info { "Player ${user.username} joined matchmaking queue (size: ${queue.size})" }
        
        Command(CommandName.MatchmakingStarted).send(socket)
        broadcastQueueUpdate()
    }

    override suspend fun removeFromQueue(socket: UserSocket) {
        val user = socket.user ?: return
        val removed = queue.removeIf { it.socket.user?.id == user.id }
        
        if (removed) {
            logger.info { "Player ${user.username} left matchmaking queue (size: ${queue.size})" }
            Command(CommandName.MatchmakingStopped).send(socket)
            broadcastQueueUpdate()
        }
    }

    override fun isInQueue(socket: UserSocket): Boolean {
        val user = socket.user ?: return false
        return queue.any { it.socket.user?.id == user.id }
    }

    override fun getQueueSize(): Int = queue.size

    private suspend fun tryCreateMatches() {
        // Группируем по режиму
        val byMode = queue.groupBy { it.mode }
        
        for ((mode, players) in byMode) {
            if (players.size >= minPlayersForMatch) {
                createMatch(players.take(minPlayersForMatch), mode)
            }
        }
    }

    private suspend fun createMatch(entries: List<MatchmakingEntry>, mode: BattleMode) {
        val handler = when (mode) {
            BattleMode.Deathmatch -> DeathmatchModeHandler.builder()
            BattleMode.TeamDeathmatch -> TeamDeathmatchModeHandler.builder()
            BattleMode.CaptureTheFlag -> CaptureTheFlagModeHandler.builder()
            BattleMode.ControlPoints -> ControlPointsModeHandler.builder()
            BattleMode.Juggernaut -> JuggernautModeHandler.builder()
        }

        // Выбираем случайную карту
        val availableMaps = mapRegistry.maps.filter { it.enabled }
        val selectedMap = availableMaps.random()

        val battle = Battle(
            coroutineContext = coroutineScope.coroutineContext,
            id = "mm_${Battle.generateId()}",
            title = "Matchmaking #${System.currentTimeMillis() % 10000}",
            map = selectedMap,
            modeHandlerBuilder = handler
        )

        battle.properties[BattleProperty.TimeLimit] = 600 // 10 минут
        battle.properties[BattleProperty.ScoreLimit] = 30
        battle.properties[BattleProperty.MaxPeople] = entries.size

        battleProcessor.battles.add(battle)
        battle.autoRestartHandler(battle)
        battle.manageBattleBonuses(battle)

        logger.info { 
            "Matchmaking: Created battle ${battle.id} for ${entries.size} players on ${selectedMap.name}" 
        }

        // Убираем из очереди и отправляем в битву
        entries.forEach { entry ->
            queue.remove(entry)
            
            Command(
                CommandName.MatchmakingFound,
                MatchmakingFoundData(
                    battleId = battle.id,
                    mapName = selectedMap.name,
                    mode = mode.key,
                    players = entries.map { it.socket.user?.username ?: "" }
                ).toJson()
            ).send(entry.socket)

            entry.socket.selectedBattle = battle
        }

        // Оповещаем всех о новой битве
        Command(CommandName.AddBattle, battle.toBattleData().toJson()).let { command ->
            server.players
                .filter { it.screen == flashtanki.server.client.Screen.BattleSelect && it.active }
                .forEach { command.send(it) }
        }
    }

    private suspend fun broadcastQueueUpdate() {
        queue.forEach { entry ->
            Command(
                CommandName.MatchmakingQueueUpdate,
                MatchmakingQueueData(queueSize = queue.size).toJson()
            ).send(entry.socket)
        }
    }
}

// Data classes для JSON
@kotlinx.serialization.Serializable
data class MatchmakingFoundData(
    val battleId: String,
    val mapName: String,
    val mode: String,
    val players: List<String>
) {
    fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(serializer(), this)
}

@kotlinx.serialization.Serializable
data class MatchmakingQueueData(
    val queueSize: Int
) {
    fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(serializer(), this)
}