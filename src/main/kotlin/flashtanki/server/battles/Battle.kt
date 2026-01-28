package flashtanki.server.battles

import kotlin.coroutines.CoroutineContext
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.BonusType
import flashtanki.server.ISocketServer
import flashtanki.server.ServerMapInfo
import flashtanki.server.ServerMapBonusPoint
import flashtanki.server.battles.bonus.*
import flashtanki.server.battles.map.IMapRegistry
import flashtanki.server.client.UserSocket
import flashtanki.server.client.SocketLocale
import flashtanki.server.battles.mode.*
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.extensions.launchDelayed
import flashtanki.server.math.Quaternion
import flashtanki.server.math.nextVector3
import flashtanki.server.math.Vector3
import flashtanki.server.toVector

enum class TankState {
  Dead,
  Respawn,
  SemiActive,
  Active
}

val TankState.tankInitKey: String
  get() = when(this) {
    TankState.Dead       -> "suicide"
    TankState.Respawn    -> "suicide"
    TankState.SemiActive -> "newcome"
    TankState.Active     -> "active"
  }

enum class BattleTeam(val id: Int, val key: String) {
  Red(0, "RED"),
  Blue(1, "BLUE"),

  None(2, "NONE");

  companion object {
    private val map = values().associateBy(BattleTeam::key)

    fun get(key: String) = map[key]
  }
}

val BattleTeam.opposite: BattleTeam
  get() {
    return when(this) {
      BattleTeam.None -> BattleTeam.None
      BattleTeam.Red  -> BattleTeam.Blue
      BattleTeam.Blue -> BattleTeam.Red
    }
  }

enum class BattleMode(val key: String, val id: Int) {
  Deathmatch("DM", 1),
  TeamDeathmatch("TDM", 2),
  CaptureTheFlag("CTF", 3),
  ControlPoints("CP", 4),
  Juggernaut("JGR", 5);

  companion object {
    private val map = values().associateBy(BattleMode::key)
    private val mapById = values().associateBy(BattleMode::id)

    fun get(key: String) = map[key]
    fun getById(id: Int) = mapById[id]
  }
}

enum class SendTarget {
  Players,
  Spectators
}

suspend fun Command.sendTo(
  battle: Battle,
  vararg targets: SendTarget = arrayOf(SendTarget.Players, SendTarget.Spectators),
  exclude: BattlePlayer? = null
): Int = battle.sendTo(this, *targets, exclude = exclude)

fun List<BattlePlayer>.users() = filter { player -> !player.isSpectator }
fun List<BattlePlayer>.spectators() = filter { player -> player.isSpectator }
fun List<BattlePlayer>.ready() = filter { player -> player.ready }
fun List<BattlePlayer>.exclude(player: BattlePlayer) = filter { it != player }

class Battle(
  coroutineContext: CoroutineContext,
  val id: String,
  val title: String,
  var map: ServerMapInfo,
  modeHandlerBuilder: BattleModeHandlerBuilder
) : KoinComponent {
  companion object {
    private const val ID_LENGTH = 16

    fun   generateId(): String {
      val randomValue = Random.nextULong()
      val hexString = randomValue.toString(16)
      return hexString.padStart(ID_LENGTH, '0')
    }
  }

  private val logger = KotlinLogging.logger { }
  private val battleProcessor by inject<IBattleProcessor>()

  private val userRepository: IUserRepository by inject()
  private val mapRegistry: IMapRegistry by inject()

  val coroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

  private var restartJob: Job? = null
  private var deleteJob: Job? = null
  private var bonusJob: Job? = null

  private var healthIndex = 0
  private var armorIndex = 0
  private var damageIndex = 0
  private var nitroIndex = 0

  val droppedGoldBoxes = mutableListOf<ServerMapBonusPoint>()
  val droppedGoldIds = mutableListOf<String>()
  val unusedGoldBoxes = mutableListOf<Int>()
  val goldBoxesIntervals = mutableListOf<Job>()

  val properties: BattleProperties = BattleProperties()
  val modeHandler: BattleModeHandler = modeHandlerBuilder(this)
  val players: MutableList<BattlePlayer> = mutableListOf()

  val damageProcessor = DamageProcessor(this)
  val bonusProcessor = BonusProcessor(this)
  val mineProcessor = MineProcessor(this)
  val fundProcessor = FundProcessor(this)

  var startTime: Instant? = Clock.System.now()
  var startedRestart: Boolean = false
  var restartEndTime: Instant = Clock.System.now()

  // Remark: Original server sends negative value if battle has no time limit
  val timeLeft: Duration?
    get() {
      val startTime = startTime ?: return null
      if(properties[BattleProperty.TimeLimit] == 0) return null
      return (startTime + properties[BattleProperty.TimeLimit].seconds) - Clock.System.now()
    }

  suspend fun manageBattleDeletion(battle: Battle) {
    if (battle.id.startsWith("")) return

    deleteJob?.cancel()

    logger.debug("Starting battle observer for battle ${battle.id}")

    deleteJob = battle.coroutineScope.launchDelayed(90.seconds) {
      if(battle.players.isNotEmpty()) {
        logger.debug("Battle has active players. Aborting deleting-observer for battle ${battle.id}")
        cancel()
      } else {
        logger.debug("No active players in battle. Remove battle ${battle.id}")

        battle.players.clear()
        battle.bonusProcessor.bonuses.clear()
        battle.mineProcessor.mines.clear()

        restartJob?.cancel()

        battleProcessor.removeBattle(battle.id)
      }
    }

    logger.debug("Battle observer set up for battle ${battle.id}")
  }

  suspend fun manageBattleBonuses(battle: Battle) {
    if(!battle.properties[BattleProperty.WithoutBonuses]) {
      bonusJob = battle.coroutineScope.launchDelayed(5.seconds) {
        battle.spawnBonuses()
      }
    }
  }
  

suspend fun createBonusRegions(socket: UserSocket) {
    val battle = this
    val healthBonusPoints = battle.map.bonuses
        .filter { bonus -> bonus.types.contains(BonusType.Health) }
        .filter { bonus -> bonus.modes.contains(if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch) }
	val armorBonusPoints = battle.map.bonuses
        .filter { bonus -> bonus.types.contains(BonusType.DoubleArmor) }
        .filter { bonus -> bonus.modes.contains(if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch) }
	val damageBonusPoints = battle.map.bonuses
        .filter { bonus -> bonus.types.contains(BonusType.DoubleDamage) }
        .filter { bonus -> bonus.modes.contains(if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch) }
    val nitroBonusPoints = battle.map.bonuses
        .filter { bonus -> bonus.types.contains(BonusType.Nitro) }
        .filter { bonus -> bonus.modes.contains(if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch) }
	for (healthBonusPoint in healthBonusPoints) {
        val x = (healthBonusPoint.position.min.x + healthBonusPoint.position.max.x) / 2
        val y = (healthBonusPoint.position.min.y + healthBonusPoint.position.max.y) / 2
        val z = (healthBonusPoint.position.min.z + healthBonusPoint.position.max.z) / 2
        Command(CommandName.AddBonusRegion, x.toString(), y.toString(), z.toString(), "health".toString()).send(socket)
    }
	
	for (armorBonusPoint in armorBonusPoints) {
        val x = (armorBonusPoint.position.min.x + armorBonusPoint.position.max.x) / 2
        val y = (armorBonusPoint.position.min.y + armorBonusPoint.position.max.y) / 2
        val z = (armorBonusPoint.position.min.z + armorBonusPoint.position.max.z) / 2
        Command(CommandName.AddBonusRegion, x.toString(), y.toString(), z.toString(), "armor".toString()).send(socket)
    }
	
	for (damageBonusPoint in damageBonusPoints) {
        val x = (damageBonusPoint.position.min.x + damageBonusPoint.position.max.x) / 2
        val y = (damageBonusPoint.position.min.y + damageBonusPoint.position.max.y) / 2
        val z = (damageBonusPoint.position.min.z + damageBonusPoint.position.max.z) / 2
        Command(CommandName.AddBonusRegion, x.toString(), y.toString(), z.toString(), "damage".toString()).send(socket)
    }

    for (nitroBonusPoint in nitroBonusPoints) {
        val x = (nitroBonusPoint.position.min.x + nitroBonusPoint.position.max.x) / 2
        val y = (nitroBonusPoint.position.min.y + nitroBonusPoint.position.max.y) / 2
        val z = (nitroBonusPoint.position.min.z + nitroBonusPoint.position.max.z) / 2
        Command(CommandName.AddBonusRegion, x.toString(), y.toString(), z.toString(), "nitro".toString()).send(socket)
    }

    if (droppedGoldBoxes.isNotEmpty() && droppedGoldIds.isNotEmpty()) {
        for (goldRegion in droppedGoldBoxes)
        {
          val x = (goldRegion.position.min.x + goldRegion.position.max.x) / 2
          val y = (goldRegion.position.min.y + goldRegion.position.max.y) / 2
          val z = (goldRegion.position.min.z + goldRegion.position.max.z) / 2
          Command(CommandName.AddOneGoldRegion, x.toString(), y.toString(), z.toString(), droppedGoldIds[droppedGoldBoxes.indexOf(goldRegion)].toString()).send(socket)
        }
    }
}

  suspend fun autoRestartHandler(battle: Battle) {
    restartJob?.cancel()
    bonusJob?.cancel()

    startedRestart = false

    restartJob = timeLeft?.let {
      battle.coroutineScope.launchDelayed(it) {
        restart()
      }
    }

    restartJob?.invokeOnCompletion { _ ->
      logger.debug("Restart job completed for battle $id")
    }
  }

    private suspend fun spawnBonuses() {
      while(true) {
        delay(30.seconds.toDouble(DurationUnit.MILLISECONDS).toLong())
        spawnBonusForTime(BonusType.Health, 40.seconds)
        spawnBonusForTime(BonusType.Nitro, 20.seconds)
        spawnBonusForTime(BonusType.DoubleArmor, 25.seconds)
        spawnBonusForTime(BonusType.DoubleDamage, 30.seconds)
        delay(1.seconds.toDouble(DurationUnit.MILLISECONDS).toLong())
      }
    }

    private suspend fun spawnBonusForTime(bonusType: BonusType, interval: Duration) {
	  // TODO(TitanoMachina)
      val battle = this
      val availableBonuses = battle.map.bonuses
        .filter { bonus -> bonus.types.contains(bonusType) }
        .filter { bonus -> bonus.modes.contains(if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch) }

      if(availableBonuses.isEmpty()) return
	  if(bonusType == BonusType.Health && healthIndex > availableBonuses.size) return
	  if(bonusType == BonusType.DoubleArmor && armorIndex > availableBonuses.size) return
	  if(bonusType == BonusType.DoubleDamage && damageIndex > availableBonuses.size) return
	  if(bonusType == BonusType.Nitro && nitroIndex > availableBonuses.size) return
      val bonusPoint = when(bonusType) {
        BonusType.Health       -> availableBonuses[healthIndex]
        BonusType.DoubleArmor  -> availableBonuses[armorIndex]
        BonusType.DoubleDamage -> availableBonuses[damageIndex]
        BonusType.Nitro        -> availableBonuses[nitroIndex]
        else                   -> throw Exception("Unsupported bonus type: $bonusType")
      }
      val x = (bonusPoint.position.min.x + bonusPoint.position.max.x) / 2
      val y = (bonusPoint.position.min.y + bonusPoint.position.max.y) / 2
      val z = (bonusPoint.position.min.z + bonusPoint.position.max.z) / 2
      val position = Vector3()
      position.reset(x, y, z)
      val rotation = Quaternion()
      rotation.fromEulerAngles(bonusPoint.rotation.toVector())

      val bonus = when(bonusType) {
        BonusType.Health       -> BattleRepairKitBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        BonusType.DoubleArmor  -> BattleDoubleArmorBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        BonusType.DoubleDamage -> BattleDoubleDamageBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        BonusType.Nitro        -> BattleNitroBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        else                   -> throw Exception("Unsupported bonus type: $bonusType")
      }

      battle.bonusProcessor.incrementId()
      battle.coroutineScope.launch {
        battle.bonusProcessor.spawn(bonus)
      }
	  when(bonusType) {
	    BonusType.Health       -> healthIndex++
        BonusType.DoubleArmor  -> armorIndex++
        BonusType.DoubleDamage -> damageIndex++
        BonusType.Nitro        -> nitroIndex++
        else                   -> throw Exception("Unsupported bonus type: $bonusType")
	  }
      delay(interval.toDouble(DurationUnit.MILLISECONDS).toLong())
    }
	
	public suspend fun spawnBonusAfterTake(bonusType: BonusType, interval: Duration, position: Vector3, rotation: Quaternion) {
	  // TODO(TitanoMachina)
	  val battle = this
      val bonus = when(bonusType) {
        BonusType.Health       -> BattleRepairKitBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        BonusType.DoubleArmor  -> BattleDoubleArmorBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        BonusType.DoubleDamage -> BattleDoubleDamageBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        BonusType.Nitro        -> BattleNitroBonus(battle, battle.bonusProcessor.nextId, position, rotation)
        else                   -> throw Exception("Unsupported bonus type: $bonusType")
      }
      battle.bonusProcessor.incrementId()
      battle.coroutineScope.launch {
	    delay(interval.toDouble(DurationUnit.MILLISECONDS).toLong())
        battle.bonusProcessor.spawn(bonus)
      }
    }

  public suspend fun spawnGoldBonus(siren:String="", withoutContainers:Boolean=false) {
      val battle = this
	  val bonusType = BonusType.Gold
      val availableBonuses = battle.map.bonuses
        .filter { bonus -> bonus.types.contains(bonusType) }
        .filter { bonus -> bonus.modes.contains(if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch) }

      if(availableBonuses.isEmpty()) return
      var bonusPoint = availableBonuses.random()
      // TODO(TitanoMachina) check bonus point in mutableList
	  while (bonusPoint in droppedGoldBoxes) {
	      if (droppedGoldBoxes.size != availableBonuses.size) {
	          bonusPoint = availableBonuses.random()
		  } else {
              unusedGoldBoxes.add(battle.bonusProcessor.nextId)
		      return
		  }
	  }
	  droppedGoldBoxes.add(bonusPoint)
      var message: String = ""
	  if (siren != "") {
	     message = siren
	  } else {
      val playersData = players.users().map { player ->
        message = when (player.socket.locale) {
          SocketLocale.Russian -> "Скоро будет сброшен золотой ящик"
          SocketLocale.English -> "Gold box will be dropped soon"
          else -> "Gold box will be dropped soon"
        }
      }
	  }
      val x = (bonusPoint.position.min.x + bonusPoint.position.max.x) / 2
      val y = (bonusPoint.position.min.y + bonusPoint.position.max.y) / 2
      val z = (bonusPoint.position.min.z + bonusPoint.position.max.z) / 2
      val position = Vector3()
      position.reset(x, y, z)
      val rotation = Quaternion()
      rotation.fromEulerAngles(bonusPoint.rotation.toVector())
	  val containerChance = Random.nextInt(1, 3)
      val bonus = if (containerChance == 2 && !withoutContainers) BattleContainerBonus(battle, battle.bonusProcessor.nextId, position, rotation, bonusPoint, message) else BattleGoldBonus(battle, battle.bonusProcessor.nextId, position, rotation, bonusPoint, message)
      battle.bonusProcessor.incrementId()
      battle.coroutineScope.launch {
        battle.bonusProcessor.spawn(bonus)
      }
  }

  fun toBattleData(): BattleData {
    // TODO(Assasans)
    return when(modeHandler) {
      is DeathmatchModeHandler -> DmBattleData(
        battleId = id,
        battleMode = modeHandler.mode,
        map = map.name,
        name = title,
        proBattle = properties[BattleProperty.ProBattle],
        privateBattle = properties[BattleProperty.privateBattle],
        maxPeople = properties[BattleProperty.MaxPeople],
        minRank = properties[BattleProperty.MinRank],
        maxRank = properties[BattleProperty.MaxRank],
        preview = map.preview,
        parkourMode = properties[BattleProperty.ParkourMode],
              timeLeft = timeLeft?.inWholeSeconds?.toInt() ?: 0,
        users = players.users().map { player -> player.user.username },
      )
	  
	  is JuggernautModeHandler -> DmBattleData(
        battleId = id,
        battleMode = modeHandler.mode,
        map = map.name,
        name = title,
        proBattle = properties[BattleProperty.ProBattle],
        privateBattle = properties[BattleProperty.privateBattle],
        maxPeople = properties[BattleProperty.MaxPeople],
        minRank = properties[BattleProperty.MinRank],
        maxRank = properties[BattleProperty.MaxRank],
        preview = map.preview,
        parkourMode = properties[BattleProperty.ParkourMode],
              timeLeft = timeLeft?.inWholeSeconds?.toInt() ?: 0,
        users = players.users().map { player -> player.user.username },
      )

      is TeamModeHandler       -> TeamBattleData(
        battleId = id,
        battleMode = modeHandler.mode,
        map = map.name,
        name = title,
        proBattle = properties[BattleProperty.ProBattle],
        privateBattle = properties[BattleProperty.privateBattle],
        maxPeople = properties[BattleProperty.MaxPeople],
        minRank = properties[BattleProperty.MinRank],
        maxRank = properties[BattleProperty.MaxRank],
        preview = map.preview,
        parkourMode = properties[BattleProperty.ParkourMode],
              timeLeft = timeLeft?.inWholeSeconds?.toInt() ?: 0,
        usersRed = players
          .users()
          .filter { player -> player.team == BattleTeam.Red }
          .map { player -> player.user.username },
        usersBlue = players
          .users()
          .filter { player -> player.team == BattleTeam.Blue }
          .map { player -> player.user.username }
      )

      else                     -> throw IllegalStateException("Unknown battle mode: ${modeHandler::class}")
    }
  }

  suspend fun selectFor(socket: UserSocket) {
    Command(CommandName.ClientSelectBattle, id).send(socket)
  }

  suspend fun showInfoFor(socket: UserSocket) {
    val info = when(modeHandler) {
      is DeathmatchModeHandler -> ShowDmBattleInfoData(
        itemId = id,
        battleMode = modeHandler.mode,
        scoreLimit = properties[BattleProperty.ScoreLimit],
        timeLimitInSec = properties[BattleProperty.TimeLimit],
        timeLeftInSec = timeLeft?.inWholeSeconds?.toInt() ?: 0,
        preview = map.preview,
        maxPeopleCount = properties[BattleProperty.MaxPeople],
        name = title,
        proBattle = properties[BattleProperty.ProBattle],

        minRank = properties[BattleProperty.MinRank],
        maxRank = properties[BattleProperty.MaxRank],
        spectator = socket.user?.permissions?.any(Permissions.Moderator.toBitfield().plus(Permissions.Superuser.toBitfield())) ?: false,
        withoutBonuses = properties[BattleProperty.WithoutBonuses],
        withoutCrystals = properties[BattleProperty.WithoutCrystals],
        withoutSupplies = properties[BattleProperty.WithoutSupplies],
        reArmorEnabled = properties[BattleProperty.RearmingEnabled],
        parkourMode = properties[BattleProperty.ParkourMode],
        users = players.users().map { player -> BattleUser(user = player.user.username, kills = player.kills, score = player.score) },
      ).toJson()
	  
	  is JuggernautModeHandler -> ShowDmBattleInfoData(
        itemId = id,
        battleMode = modeHandler.mode,
        scoreLimit = properties[BattleProperty.ScoreLimit],
        timeLimitInSec = properties[BattleProperty.TimeLimit],
        timeLeftInSec = timeLeft?.inWholeSeconds?.toInt() ?: 0,
        preview = map.preview,
        maxPeopleCount = properties[BattleProperty.MaxPeople],
        name = title,
        proBattle = properties[BattleProperty.ProBattle],

        minRank = properties[BattleProperty.MinRank],
        maxRank = properties[BattleProperty.MaxRank],
        spectator = socket.user?.permissions?.any(Permissions.Moderator.toBitfield().plus(Permissions.Superuser.toBitfield())) ?: false,
        withoutBonuses = properties[BattleProperty.WithoutBonuses],
        withoutCrystals = properties[BattleProperty.WithoutCrystals],
        withoutSupplies = properties[BattleProperty.WithoutSupplies],
        reArmorEnabled = properties[BattleProperty.RearmingEnabled],
        parkourMode = properties[BattleProperty.ParkourMode],
        users = players.users().map { player -> BattleUser(user = player.user.username, kills = player.kills, score = player.score) },
      ).toJson()

      is TeamModeHandler       -> ShowTeamBattleInfoData(
        itemId = id,
        battleMode = modeHandler.mode,
        scoreLimit = properties[BattleProperty.ScoreLimit],
        timeLimitInSec = properties[BattleProperty.TimeLimit],
        timeLeftInSec = timeLeft?.inWholeSeconds?.toInt() ?: 0,
        preview = map.preview,
        maxPeopleCount = properties[BattleProperty.MaxPeople],
        name = title,
        proBattle = properties[BattleProperty.ProBattle],
        minRank = properties[BattleProperty.MinRank],
        maxRank = properties[BattleProperty.MaxRank],
        spectator = socket.user?.permissions?.any(Permissions.Moderator.toBitfield().plus(Permissions.Superuser.toBitfield())) ?: false,
        withoutBonuses = properties[BattleProperty.WithoutBonuses],
        withoutCrystals = properties[BattleProperty.WithoutCrystals],
        withoutSupplies = properties[BattleProperty.WithoutSupplies],
        reArmorEnabled = properties[BattleProperty.RearmingEnabled],
        parkourMode = properties[BattleProperty.ParkourMode],
        usersRed = players
          .users()
          .filter { player -> player.team == BattleTeam.Red }
          .map { player -> BattleUser(user = player.user.username, kills = player.kills, score = player.score) },
        usersBlue = players
          .users()
          .filter { player -> player.team == BattleTeam.Blue }
          .map { player -> BattleUser(user = player.user.username, kills = player.kills, score = player.score) },
        scoreRed = modeHandler.teamScores[BattleTeam.Red] ?: 0,
        scoreBlue = modeHandler.teamScores[BattleTeam.Blue] ?: 0,
        autoBalance = properties[BattleProperty.AutoBalance],
        friendlyFire = properties[BattleProperty.FriendlyFireEnabled]
      ).toJson()

      else                     -> throw IllegalStateException("Unknown battle mode: ${modeHandler.mode}")
    }

    Command(CommandName.ShowBattleInfo, info).send(socket)
  }

  suspend fun clearGoldIntervals() {
    var goldIntervalsLength = 0
    while (goldIntervalsLength <= goldBoxesIntervals.size) {
      goldBoxesIntervals[goldIntervalsLength].cancel()
      goldIntervalsLength++
      if (goldIntervalsLength >= goldBoxesIntervals.size) {
        goldBoxesIntervals.clear()
      }
    }
    logger.debug { "In battle $id removed all gold intervals" }
  }
  
  suspend fun removeBonusesFromMap() {
      var bonusesLen = 0
  	  while (bonusesLen < bonusProcessor.bonuses.size) {
	        bonusProcessor.bonuses[bonusesLen]?.removeThis()
			bonusesLen++
	  }
  }

  suspend fun restart() {
    val restartTime = 10.seconds

    startedRestart = true
    restartEndTime = Clock.System.now() + restartTime

    val playerPrizeList = fundProcessor.calculateFund(this, modeHandler)

    val playersData = players.users().map { player ->
      val username = player.user.username

      val prizePair = playerPrizeList.find { it.first == username }
      val prizeAmount = prizePair?.second ?: 0.0

      player.user.crystals += if (player.user.hasPremium()) prizeAmount.roundToInt() * 2 else prizeAmount.roundToInt()
      player.socket.updateCrystals()
      userRepository.updateUser(player.user)
      if (player.socket.weaponDelayMount != 0) {
        player.socket.weaponDelayMount = 0
      }
      if (player.socket.hullDelayMount != 0) {
        player.socket.hullDelayMount = 0
      }
      if (player.socket.colormapDelayMount != 0) {
        player.socket.colormapDelayMount = 0
      }
      FinishBattleUserData(
        username = player.user.username,
        rank = player.user.rank.value,
        team = player.team,
        score = player.score,
        kills = player.kills,
        deaths = player.deaths,
        prize = prizeAmount.roundToInt(),
        bonus_prize = 0//if (player.user.hasPremium() && prizeAmount.roundToInt().isNaN()) 0.0 else prizeAmount.roundToInt() // Specify the bonus prize if applicable
      )
    }

    Command(
      CommandName.FinishBattle,
      FinishBattleData(
        time_to_restart = restartTime.inWholeMilliseconds,
        users = playersData
      ).toJson()
    ).sendTo(this)

    logger.debug { "Finished battle $id" }

    coroutineScope.launchDelayed(restartTime) {
      if(modeHandler is TeamModeHandler) {
        modeHandler.teamScores.replaceAll { _, _ -> 0 }
      }

      (modeHandler as? CaptureTheFlagModeHandler)?.let { ctfModeHandler ->
        ctfModeHandler.flags
          .filter { (team, flag) -> flag !is FlagOnPedestalState }
          .forEach { (team, flag) -> ctfModeHandler.returnFlag(team, carrier = null) }
      }
    }
	removeBonusesFromMap()
	if (goldBoxesIntervals.isNotEmpty()) {
        clearGoldIntervals()
	}	
	droppedGoldBoxes.clear()
    droppedGoldIds.clear()
    unusedGoldBoxes.clear()
	healthIndex = 0
    armorIndex = 0
    damageIndex = 0
    nitroIndex = 0
    delay(restartTime.inWholeMilliseconds)

    startTime = Clock.System.now()
    players.users().forEach { player ->
      with(player) {
        kills = 0
        deaths = 0
        score = 0
        updateStats()
        battle.mineProcessor.deactivateAll(this, false)
        respawn()
      }
    }

    fundProcessor.fund = 0
    fundProcessor.updateFund()

    Command(CommandName.RestartBattle, properties[BattleProperty.TimeLimit].toString()).sendTo(this)
	
	/*players.users().forEach { player ->
	   with(player) {
	     ultimateCharge = 0
	     tank!!.updateUltimateCharge()
	   }
	}*/

    autoRestartHandler(this)

    logger.debug { "Restarted battle $id" }
  }

  suspend fun sendTo(
    command: Command,
    vararg targets: SendTarget = arrayOf(SendTarget.Players, SendTarget.Spectators),
    exclude: BattlePlayer? = null
  ): Int {
    var count = 0
    if(targets.contains(SendTarget.Players)) {
      players
        .users()
        .filter { player -> player.socket.active }
        .filter { player -> exclude == null || player != exclude }
        .filter { player -> player.ready }
        .forEach { player ->
          command.send(player)
          count++
        }
    }
    if(targets.contains(SendTarget.Spectators)) {
      players
        .spectators()
        .filter { player -> player.socket.active }
        .filter { player -> exclude == null || player != exclude }
        .filter { player -> player.ready }
        .forEach { player ->
          command.send(player)
          count++
        }
    }

    return count
  }
}

interface IBattleProcessor {
  val battles: MutableList<Battle>

  fun getBattle(id: String): Battle?

  suspend fun removeBattle(id: String)
}

class BattleProcessor : IBattleProcessor, KoinComponent {
  private val logger = KotlinLogging.logger { }
  private val server: ISocketServer by inject()

  override val battles: MutableList<Battle> = mutableListOf()

  override fun getBattle(id: String): Battle? = battles.singleOrNull { battle -> battle.id == id }

  override suspend fun removeBattle(id: String) {
    val battle = getBattle(id)
    if(battle != null) {
      battles.remove(battle)

      Command(CommandName.HideBattle, id)
        .let { command ->
          server.players
            .filter { player -> player.active }
            .filter { player -> player.screen != Screen.Battle }
            .filter { player -> player.selectedBattle == battle }
            .forEach { player ->
              command.send(player)
              player.selectedBattle = null
            }
        }

      Command(CommandName.DeleteBattle, id)
        .let { command ->
          server.players
            .filter { player -> player.active }
            .forEach { player -> command.send(player) }
        }
      logger.info("Battle $id has been removed.")
    } else {
      logger.warn("Battle $id not found.")
    }
  }
}
