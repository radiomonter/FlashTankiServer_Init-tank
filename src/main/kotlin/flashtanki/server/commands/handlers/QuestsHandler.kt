package flashtanki.server.commands.handlers

import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import flashtanki.server.client.UserSocket
import flashtanki.server.client.send
import flashtanki.server.client.toJson
import flashtanki.server.commands.Command
import flashtanki.server.commands.CommandHandler
import flashtanki.server.commands.CommandName
import flashtanki.server.commands.ICommandHandler
import flashtanki.server.quests.*
import flashtanki.server.client.*

class QuestsHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }
  private val userRepository: IUserRepository by inject()
  private val questConverter: IQuestConverter by inject()
  private val randomQuestService: IRandomQuestService by inject()

  @CommandHandler(CommandName.OpenQuests)
  suspend fun openQuests(socket: UserSocket) {
    val user = socket.user ?: throw Exception("No User")
    val locale = socket.locale ?: throw IllegalStateException("Socket locale is null")
	user.dailyQuests
      .forEach { quest ->
         if (quest.current >= quest.required) {
		    quest.current = quest.required
			quest.completed = true
		 }
      }
    if (user.dailyQuests.size > 0) {
      Command(
              CommandName.ClientOpenQuests,
              OpenQuestsData(
                      weeklyQuestDescription = WeeklyQuestDescriptionData(doneForToday = if (user.dailyQuests.size < 3) true else false, currentQuestLevel = user.currentQuestLevel, currentQuestStreak = user.currentQuestStreak),
                      quests = user.dailyQuests
                              .sortedBy { quest -> quest.id }
                              .map { quest -> questConverter.toClientDailyQuest(quest, locale, user) }
              ).toJson()
      ).send(socket)
    } else {
      Command(CommandName.ClientOpenQuestsWithoutQuests,
              OpenQuestsData(
                  weeklyQuestDescription = WeeklyQuestDescriptionData(doneForToday = if (user.dailyQuests.size < 3) true else false, currentQuestLevel = user.currentQuestLevel, currentQuestStreak = user.currentQuestStreak),
                  quests = listOf()
              ).toJson()
      ).send(socket)
    }
  }

  @CommandHandler(CommandName.SkipQuestFree)
  suspend fun skipQuestFree(socket: UserSocket, questId: Int) {
    // TODO(Assasans)
    val user = socket.user ?: throw Exception("No User")
    val id = if (questId <= 0 || questId >= user.dailyQuests.size) 0 else (questId - 1)
    val randQuest = randomQuestService.getRandomQuest(id, socket)
    user.dailyQuests.set(id, randQuest)
    user.canSkipQuestForFree = true
    userRepository.updateUser(user)
    Command(
      CommandName.ClientSkipQuest,
      SkipDailyQuestResponseData(
        questId = questId,
        quest = questConverter.toClientDailyQuest(randQuest, socket.locale ?: throw Exception("No Locale"), user)
      ).toJson()
    ).send(socket)
    socket.updateQuests()
  }

  @CommandHandler(CommandName.SkipQuestPaid)
  suspend fun skipQuestPaid(socket: UserSocket, questId: Int, price: Int) {
    val user = socket.user ?: throw Exception("No User")
    if (user.dailyQuests.isEmpty()) {
      throw Exception("No daily quests available")
    }
    val idToSkip = if (questId <= 0 || questId > user.dailyQuests.size) 0 else (questId - 1)
    val randQuest = randomQuestService.getRandomQuest(idToSkip, socket)
    user.dailyQuests.set(idToSkip, randQuest)
    user.crystals -= price
    userRepository.updateUser(user)
    socket.updateCrystals()
    Command(
      CommandName.ClientSkipQuest,
      SkipDailyQuestResponseData(
        questId = questId,
        quest = questConverter.toClientDailyQuest(randQuest, socket.locale ?: throw Exception("No Locale"), user)
      ).toJson()
    ).send(socket)
    socket.updateQuests()

  if (questId <= 1){
    return
  }
}



  @CommandHandler(CommandName.QuestTakePrize)
  suspend fun questTakePrize(socket: UserSocket, questId: Int) {
    val user = socket.user ?: throw Exception("User is null")
    val id = if (questId <= 0 || questId > user.dailyQuests.size) 0 else (questId - 1)
    val quest = user.dailyQuests[id]

    for (reward in quest.rewards) {
      if (reward.type == ServerDailyRewardType.Crystals) {
        user.crystals += reward.count
        socket.updateCrystals() // Перемещено внутрь блока обновления кристаллов
      }
    }

    userRepository.updateUser(user)

    if (user.dailyQuests.size == 3) {
      user.currentQuestStreak++
      if (user.currentQuestStreak == 7) {
        user.currentQuestStreak = 0
        if (user.currentQuestLevel == 4) {
          user.currentQuestLevel = 0
        } else {
          user.currentQuestLevel++
        }
      }
    }

    user.dailyQuests.removeAt(id)
    socket.updateQuests()
    Command(CommandName.ClientQuestTakePrize, questId.toString()).send(socket)
  }
}

