package flashtanki.server.quests

import org.koin.core.component.KoinComponent
import flashtanki.server.client.*
import kotlin.random.Random

interface IQuestConverter {
  fun   toClientDailyQuest(quest: ServerDailyQuest, locale: SocketLocale, user: User): DailyQuest
}

class QuestConverter : IQuestConverter, KoinComponent {
  override fun toClientDailyQuest(quest: ServerDailyQuest, locale: SocketLocale, user: User): DailyQuest {
    // TODO(Assasans): Quest information
    return DailyQuest(
      canSkipForFree = !user.canSkipQuestForFree,
      description = quest.description.get(locale),
      finishCriteria = quest.required,
      image = quest.preview,
      questId = quest.id.toInt() + Random.nextInt(1, 300),
      progress = quest.current,
      skipCost = 1000,
      prizes = quest.rewards
        .sortedBy { reward -> reward.index }
        .map { reward ->
          DailyQuestPrize(
            name = reward.type.name,
            count = reward.count
          )
        }
    )
  }
}
