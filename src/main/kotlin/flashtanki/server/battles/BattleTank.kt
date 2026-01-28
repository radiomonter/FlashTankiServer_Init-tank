package flashtanki.server.battles

import kotlin.math.floor
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.ISocketServer
import flashtanki.server.battles.effect.TankEffect
import flashtanki.server.battles.mode.*
import flashtanki.server.battles.weapons.WeaponHandler
import kotlin.time.Duration.Companion.seconds
import flashtanki.server.client.*
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.garage.ServerGarageUserItemHull
import flashtanki.server.garage.ServerGarageUserItemPaint
import flashtanki.server.garage.ServerGarageUserItemResistance
import flashtanki.server.math.Quaternion
import flashtanki.server.math.Vector3
import flashtanki.server.math.distanceTo
import flashtanki.server.quests.KillEnemyQuest
import flashtanki.server.extensions.launchDelayed
import flashtanki.server.quests.questOf
import flashtanki.server.toVector

object TankConstants {
  const val MAX_HEALTH: Double = 10000.0
}

class BattleTank(
  val id: String,
  val player: BattlePlayer,
  val incarnation: Int = 1,
  var state: TankState,
  var position: Vector3,
  var orientation: Quaternion,
  val hull: ServerGarageUserItemHull,
  val weapon: WeaponHandler,
  val coloring: ServerGarageUserItemPaint,
  val resistance: ServerGarageUserItemResistance,
  var health: Double = hull.modification.maxHealth
) : KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val server: ISocketServer by inject()
  private val userRepository by inject<IUserRepository>()

  val socket: UserSocket
    get() = player.socket

  val battle: Battle
    get() = player.battle

  val coroutineScope = CoroutineScope(player.coroutineScope.coroutineContext + SupervisorJob())

  val effects: MutableList<TankEffect> = mutableListOf()

  var selfDestructing: Boolean = false

  val clientHealth: Int
    get() = floor((health / hull.modification.maxHealth) * TankConstants.MAX_HEALTH).toInt()

  suspend fun activate() {
    if(state == TankState.Active) return

    state = TankState.Active

    player.battle.players.users().forEach { player ->
      val tank = player.tank
      if(tank != null && tank != this) {
        Command(CommandName.ClientActivateTank, tank.id).send(socket)
      }
    }

    Command(CommandName.ClientActivateTank, id).sendTo(battle)
  }

  suspend fun deactivate(terminate: Boolean = false) {
    coroutineScope.cancel()

    if(!terminate) {
      effects.forEach { effect ->
        effect.deactivate()
      }
    }
    effects.clear()

    if(terminate || battle.properties[BattleProperty.DeactivateMinesOnDeath]) {
      battle.mineProcessor.deactivateAll(player)
    }
  }

  private suspend fun killSelf() {
    deactivate()
    state = TankState.Dead

    player.deaths++
    player.updateStats()

    (battle.modeHandler as? CaptureTheFlagModeHandler)?.let { handler ->
      val flag = handler.flags[player.team.opposite]
      if(flag is FlagCarryingState && flag.carrier == this) {
        handler.dropFlag(flag.team, this, position)
      }
    }
    Command(CommandName.KillLocalTank).send(socket)
  }

  suspend fun killBy(killer: BattleTank) {
    killSelf()

    Command(
      CommandName.KillTank,
      id,
      TankKillType.ByPlayer.key,
      killer.id,
      killer.weapon.item.mountName.substringBeforeLast("_")
    ).sendTo(battle)

    if (battle.modeHandler is JuggernautModeHandler && battle.modeHandler.mode == BattleMode.Juggernaut)
    {
      val mh = (battle.modeHandler as JuggernautModeHandler)
      if (killer.player.user.username != player.user.username) {
        mh.addBossKillsAndCheckKillStreak(killer.player.user.username)
      }
      if (mh.bossId == player.user.username)
      {
        mh.bossId = killer.player.user.username
        mh.bossKills = 0
        Command(CommandName.BossKilled).send(battle.players.ready())
        Command(CommandName.BattleMessage, 0xFF00.toString(), "Ты следующий Джаггернаут, приготовься!").send(killer)
        killer.selfDestructing = true
        killer.coroutineScope.launchDelayed(3.seconds) {
          killer.selfDestructing = false
          killer.selfDestruct(silent = true)
        }
      }
    }

    killer.player.kills = when {
      id == killer.id && killer.player.kills > 0 -> killer.player.kills - 1
      id != killer.id                            -> killer.player.kills + 1
      else                                       -> killer.player.kills
    }



    if(killer.id != id && battle.players.count { it.team == player.team.opposite } != 0 && !battle.properties[BattleProperty.ParkourMode]) {
      val fund = when(player.user.rank.value) {
        in UserRank.Recruit.value..UserRank.Sergeant.value                 -> 3
        in UserRank.StaffSergeant.value..UserRank.WarrantOfficer1.value    -> 5
        in UserRank.WarrantOfficer2.value..UserRank.SecondLieutenant.value -> 7
        in UserRank.Captain.value..UserRank.Generalissimo.value            -> 9
        else                                                               -> 6
      }

      battle.fundProcessor.fund += fund
      battle.fundProcessor.updateFund()

      killer.player.score += 10
      killer.player.updateStats()

      killer.player.user.score += 10
      killer.player.socket.updateScore()

      userRepository.updateUser(killer.player.user)

      player.user.questOf<KillEnemyQuest> { quest ->
        quest.mode == null || quest.mode == battle.modeHandler.mode
      }?.let { quest ->
        quest.current++
        socket.updateQuests()
        quest.updateProgress()
      }
    }

    if(battle.modeHandler is TeamModeHandler) {
      val handler = battle.modeHandler
      if(handler is TeamDeathmatchModeHandler) {
        handler.updateScores(killer.player, player)
      }
    }

    Command(
      CommandName.UpdatePlayerKills,
      battle.id,
      killer.player.user.username,
      killer.player.kills.toString()
    ).let { command ->
      server.players
        .filter { player -> player.screen == Screen.BattleSelect }
        .filter { player -> player.active }
        .forEach { player -> command.send(player) }
    }
  }

  suspend fun killByKillZone() {
    if(state == TankState.Dead) return

    when(val modeHandler = battle.modeHandler) {
      is TeamDeathmatchModeHandler -> modeHandler.decreaseScore(player)
      is CaptureTheFlagModeHandler -> {
        val flag = modeHandler.flags[player.team.opposite]
        if(flag is FlagCarryingState) {
          modeHandler.returnFlag(flag.team, null)
        }
      }
    }

    player.kills = maxOf(player.kills - 1, 0)

    killSelf()

    logger.debug("Tank (ID: $id) was destroyed by kill-zone")

    Command(
      CommandName.KillTank,
      id,
      TankKillType.SelfDestruct.key,
      id,
      ""
    ).sendTo(battle)
  }

  suspend fun selfDestruct(silent: Boolean = false) {
    if(state == TankState.Dead) return
    killSelf()
    if(silent) {
      Command(CommandName.KillTankSilent, id).sendTo(battle)
    } else {
      player.kills = maxOf(player.kills - 1, 0)
      Command(
        CommandName.KillTank,
        id,
        TankKillType.SelfDestruct.key,
        id,
        ""
      ).sendTo(battle)

      if(battle.modeHandler is TeamModeHandler) {
        val handler = battle.modeHandler
        if(handler !is TeamDeathmatchModeHandler) return
        handler.decreaseScore(player)
      }
      if (battle.modeHandler is JuggernautModeHandler && battle.modeHandler.mode == BattleMode.Juggernaut)
      {
        val mh = (battle.modeHandler as JuggernautModeHandler)
        if (mh.bossId == player.user.username)
        {
          mh.bossId = ""
          mh.bossKills = 0
          Command(CommandName.BossKilled).send(battle.players.ready())
        }
      }
    }
  }

  fun updateSpawnPosition() {
    // TODO(Assasans): Special handling for CP: https://web.archive.org/web/20160310101712/http://ru.tankiwiki.com/%D0%9A%D0%BE%D0%BD%D1%82%D1%80%D0%BE%D0%BB%D1%8C_%D1%82%D0%BE%D1%87%D0%B5%D0%BA
    val point = battle.map.spawnPoints
      .filter { point -> point.mode == null || point.mode == if (battle.modeHandler.mode != BattleMode.Juggernaut) battle.modeHandler.mode else BattleMode.Deathmatch }
      .filter { point -> point.team == null || point.team == player.team }
      .random()
    position = point.position.toVector()
    position.z += 200
    orientation.fromEulerAngles(point.position.toVector())

    logger.debug { "Spawn point: $position, $orientation" }
  }

  suspend fun prepareToSpawn() {
    Command(
      CommandName.PrepareToSpawn,
      id,
      "${position.x}@${position.y}@${position.z}@${orientation.toEulerAngles().z}"
    ).send(this)
  }

  /*suspend fun updateUltimateCharge() {
    val chargeToAdd = 1
    val delayTime = 1000L
    var chargeAccumulator = 0

    while (player.ultimateCharge < 100 && player.isActive) {
      val initialCharge = player.ultimateCharge

      delay(delayTime)
      chargeAccumulator += chargeToAdd
      player.ultimateCharge = (initialCharge + chargeAccumulator).coerceAtMost(100)

      if (player.ultimateCharge != initialCharge) {
        Command(CommandName.AddUltimateCharge, chargeAccumulator.toString()).send(this)
        chargeAccumulator = 0
      }
    }

    if (player.ultimateCharge >= 100) {
      Command(CommandName.ShowUltimateCharged, id).send(this)
    }
  }*/

  suspend fun initSelf() {
    Command(
      CommandName.InitTank,
      getInitTank().toJson()
    ).send(battle.players.ready())
  }

  suspend fun spawn() {
    state = TankState.SemiActive

    // TODO(Assasans): Add spawn event?
    if(player.equipmentChanged) {
      player.equipmentChanged = false
      player.changeEquipment()
    }

    updateHealth()

    Command(
      CommandName.SpawnTank,
      getSpawnTank().toJson()
    ).send(battle.players.ready())
	
	if (battle.modeHandler is JuggernautModeHandler && battle.modeHandler.mode == BattleMode.Juggernaut)
	{
	    val mh = (battle.modeHandler as JuggernautModeHandler)
	    if (mh.bossId == player.user.username) {
          Command(CommandName.BossChanged, mh.bossId).send(battle.players.ready())
        } else {
          if (mh.bossId == "") {
            mh.bossId = player.user.username
            Command(CommandName.BossChanged, mh.bossId).send(battle.players.ready())
          }
        }
	}
  }

  suspend fun updateHealth() {
    logger.debug { "Updating health for tank $id (player: ${player.user.username}): $health HP / ${hull.modification.maxHealth} HP -> $clientHealth" }

    Command(
      CommandName.ChangeHealth,
      id,
      clientHealth.toString()
    ).apply {
      send(this@BattleTank)
      sendTo(battle, SendTarget.Spectators)
      if(battle.modeHandler is TeamModeHandler) {
        battle.players
          .filter { player -> player.team == this@BattleTank.player.team }
          .filter { player -> player != this@BattleTank.player }
          .forEach { player -> send(player.socket) }
      }
    }
  }
}

fun BattleTank.distanceTo(another: BattleTank): Double {
  return position.distanceTo(another.position)
}

fun BattleTank.getInitTank() = InitTankData(
  battleId = battle.id,
  hull_id = hull.mountName,
  turret_id = weapon.item.mountName,
  colormap_id = (coloring.marketItem.animatedColoring ?: coloring.marketItem.coloring),
  hullResource = hull.modification.object3ds,
  turretResource = weapon.item.modification.object3ds,
  partsObject = TankSoundsData().toJson(),
  tank_id = id,
  nickname = player.user.username,
  team_type = player.team,
  state = state.tankInitKey,
  health = clientHealth,

  // Hull physics
  maxSpeed = hull.modification.physics.speed,
  maxTurnSpeed = hull.modification.physics.turnSpeed,
  acceleration = hull.modification.physics.acceleration,
  reverseAcceleration = hull.modification.physics.reverseAcceleration,
  sideAcceleration = hull.modification.physics.sideAcceleration,
  turnAcceleration = hull.modification.physics.turnAcceleration,
  reverseTurnAcceleration = hull.modification.physics.reverseTurnAcceleration,
  dampingCoeff = hull.modification.physics.damping,
  mass = hull.modification.physics.mass,
  power = hull.modification.physics.power,

  // Weapon physics
  turret_turn_speed = weapon.item.modification.physics.turretRotationSpeed,
  turretTurnAcceleration = weapon.item.modification.physics.turretTurnAcceleration,
  kickback = weapon.item.modification.physics.kickback,
  impact_force = weapon.item.modification.physics.impactForce,

  // Weapon visual
  sfxData = (weapon.item.modification.visual ?: weapon.item.marketItem.modifications[0]!!.visual)!!.toJson() // TODO(Assasans)
)

fun BattleTank.getSpawnTank() = SpawnTankData(
  tank_id = id,
  health = clientHealth,
  incration_id = player.incarnation,
  team_type = player.team,
  x = position.x,
  y = position.y,
  z = position.z,
  rot = orientation.toEulerAngles().z,

  // Hull physics
  speed = hull.modification.physics.speed,
  turn_speed = hull.modification.physics.turnSpeed,
  acceleration = hull.modification.physics.acceleration,
  reverseAcceleration = hull.modification.physics.reverseAcceleration,
  sideAcceleration = hull.modification.physics.sideAcceleration,
  turnAcceleration = hull.modification.physics.turnAcceleration,
  reverseTurnAcceleration = hull.modification.physics.reverseTurnAcceleration,

  // Weapon physics
  turret_rotation_speed = weapon.item.modification.physics.turretRotationSpeed,
  turretTurnAcceleration = weapon.item.modification.physics.turretTurnAcceleration
)
