package flashtanki.server.battles.mode

import flashtanki.server.ServerMapDominationPoint
import flashtanki.server.battles.Battle
import flashtanki.server.battles.BattleMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import flashtanki.server.battles.BattlePlayer
import flashtanki.server.battles.BattleTank
import flashtanki.server.client.*
import flashtanki.server.battles.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.*
import flashtanki.server.quests.*
import mu.KotlinLogging
import flashtanki.server.battles.sendTo
import flashtanki.server.battles.BattleTeam
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.ICommandHandler

data class PointState(
  val info: ServerMapDominationPoint,
  val occupated_users: MutableList<String> = mutableListOf<String>(),
  var progress: Int = 0,
        var state: String = "neutral"
)

fun PointState.toDomPoint(): DomPoint {
  return DomPoint(
    id = info.id,
    radius = info.distance,
    x = info.position.x,
    y = info.position.y,
    z = info.position.z,
    score = progress,
    state = state,
    occupated_users = occupated_users
  )
}

class ControlPointsModeHandler(battle: Battle) : ICommandHandler, TeamModeHandler(battle) {
  companion object {
    fun builder(): BattleModeHandlerBuilder = { battle -> ControlPointsModeHandler(battle) }
  }

  override val mode: BattleMode get() = BattleMode.ControlPoints

  var points = mutableListOf<PointState>()
    private val logger = KotlinLogging.logger { }
    var pointJob: Job = GlobalScope.launch {}
    var pointJobs = mutableListOf<Job>()

    init {
    val mapPoints = battle.map.points ?: throw IllegalStateException("Map has no domination points")
    points += mapPoints.map { point -> PointState(point) }
  }

  fun getPointId(point: Int) : String {
    when (point) {
        0 -> return "A".toString()
        1 -> return "B".toString()
        2 -> return "C".toString()
		3 -> return "D".toString()
		4 -> return "E".toString()
		5 -> return "F".toString()
		6 -> return "G".toString()
        else -> return "A".toString()
    }
	return "A".toString()
  }

  suspend fun tankCapturingPoint(tank: BattleTank, pointId: Int) {
      val name = tank.id.toString()
	  val fundProcessor = tank.battle.fundProcessor
      val point = points[pointId]
      Command(
              CommandName.TankCapturingPoint,
              getPointId(pointId),
              name
      ).sendTo(tank.battle)
      pointJob = tank.battle.coroutineScope.launch {
          if (tank.player.team == BattleTeam.Red) {
              while (point.progress >= -100) {
                  delay(25)
                  point.progress--
                  if (point.progress == 0) {
                      lostBy(getPointId(pointId), getTeamByTank(tank), tank)
					  point.state = "neutral"
                  }
                  Command(CommandName.SetPoinScore,
                          getPointId(pointId),
                          point.progress.toString(),
                          0.toString()
                  ).sendTo(tank.battle)
                  if (point.progress == -100) {
                      capturedBy(getPointId(pointId), getTeamByTank(tank), tank)
                      point.state = getTeamByTank(tank)
					  val enemyPlayerCount = tank.battle.players.size
					  if(enemyPlayerCount > 1 && !battle.properties[BattleProperty.ParkourMode]) {
					  fundProcessor.fund += 8
                      fundProcessor.updateFund()
					  }
					  tank.player.user.questOf<CapturePointQuest>()?.let { quest ->
                           quest.current++
                           tank.socket.updateQuests()
                           quest.updateProgress()
                      }
					  addScoreToTeam(point, tank)
                      continue
                  }
              }
          }
              if (tank.player.team == BattleTeam.Blue) {
                  while (point.progress <= 100) {
                      delay(25)
                      point.progress++
                      if (point.progress == 0) {
                          lostBy(getPointId(pointId), getTeamByTank(tank), tank)
						  point.state = "neutral"
                      }
                      Command(CommandName.SetPoinScore,
                              getPointId(pointId),
                              point.progress.toString(),
                              0.toString()
                      ).sendTo(tank.battle)
                      if (point.progress == 100) {
                          capturedBy(getPointId(pointId), getTeamByTank(tank), tank)
                          point.state = getTeamByTank(tank)
					  val enemyPlayerCount = tank.battle.players.size
					  if(enemyPlayerCount > 1 && !battle.properties[BattleProperty.ParkourMode]) {
					  fundProcessor.fund += 8
                      fundProcessor.updateFund()
					  }
					  					  tank.player.user.questOf<CapturePointQuest>()?.let { quest ->
                           quest.current++
                           tank.socket.updateQuests()
                           quest.updateProgress()
                      }
						  addScoreToTeam(point, tank)
                          continue
                      }
                  }
              }
      }
      pointJobs.add(pointJob)
      point.occupated_users.add(name)
    }

	suspend fun addScoreToTeam(point: PointState, tank: BattleTank) {
	 val scoreLimit = tank.battle.properties[BattleProperty.ScoreLimit]
	 while (point.progress <= -100 || point.progress >= 100) {
	 delay(2000)
	 teamScores.merge(tank.player.team, 1, Int::plus)
	 updateScores()
    when {
      scoreLimit != 0 && teamScores[tank.player.team] == scoreLimit && battle.modeHandler is ControlPointsModeHandler -> {
        tank.battle.restart()
		for (point in points) {
		    point.occupated_users.clear()
			point.state = "neutral"
		    point.progress = 0
		}
		for (job in pointJobs) {
		    job.cancel()
			pointJobs.remove(job)
		}
      }
    }
	}
	}

    suspend fun tankLeaveCapturingPoint(tank: BattleTank, pointId: Int) {
        //TODO(PACEQ) Leave from point
        val name = tank.id.toString()
        val point = points[pointId]
        Command(
                CommandName.TankLeaveCapturingPoint,
                name,
                getPointId(pointId)
        ).sendTo(tank.battle)
        pointJobs[point.occupated_users.indexOf(tank.id.toString())].cancel()
        pointJobs.removeAt(point.occupated_users.indexOf(tank.id.toString()))
        point.occupated_users.remove(name)
        if (point.occupated_users.isEmpty() && (!(point.progress >= 100 || point.progress <= -100))) { //TODO(TitanoMachina) Check occupated_users and point progress
		   if (point.state == "neutral") { //if point state == neutral
            while (point.progress != 0) {
                delay(25)
                when (tank.player.team) {
                    BattleTeam.Red -> point.progress++
                    BattleTeam.Blue -> point.progress--
                }
                Command(CommandName.SetPoinScore,
                        getPointId(pointId),
                        point.progress.toString(),
                        0.toString()
                ).sendTo(tank.battle)
            }
        }
		if (point.state == "red") { //if point state == red
		    while (point.progress > -100) {
                point.progress--
                Command(CommandName.SetPoinScore,
                        getPointId(pointId),
                        point.progress.toString(),
                        0.toString()
                ).sendTo(tank.battle)
			}
		}
		if (point.state == "blue") { //if point state == blue
		    while (point.progress < 100) {
                point.progress++
                Command(CommandName.SetPoinScore,
                        getPointId(pointId),
                        point.progress.toString(),
                        0.toString()
                ).sendTo(tank.battle)
			}
		}
    }
	}

    fun getTeamByPointState(point: PointState) : BattleTeam {
        when (point.state) {
            "red".toString() -> return BattleTeam.Red
            "blue".toString() -> return BattleTeam.Blue
        }
        return BattleTeam.None
    }

    fun getTeamByTank(tank: BattleTank) : String {
        when (tank.player.team) {
            BattleTeam.Red  -> return "red".toString()
            BattleTeam.Blue -> return "blue".toString()
        }
        return "neutral".toString()
    }

   suspend fun capturedBy(id: String, by: String, tank: BattleTank) {
        Command(
                CommandName.PointCapturedBy,
                by.toString(),
                id.toString()
        ).sendTo(tank.battle)
    }

    suspend fun lostBy(id: String, by: String, tank: BattleTank) {
        Command(
                CommandName.PointLostBy,
                by.toString(),
                id.toString()
        ).sendTo(tank.battle)
    }

  override suspend fun initModeModel(player: BattlePlayer) {
    Command(
      CommandName.InitDomModel,
      InitDomModelData(
        resources = DomModelResources().toJson(),
        lighting = DomModelLighting().toJson(),
        points = points.map { point -> point.toDomPoint() },
        mine_activation_radius = 5
      ).toJson()
    ).send(player)
  }
}
