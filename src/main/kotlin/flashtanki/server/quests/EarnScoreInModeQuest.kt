package flashtanki.server.quests

import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import flashtanki.server.battles.BattleMode
import flashtanki.server.client.SocketLocale
import flashtanki.server.client.User
import flashtanki.server.serialization.database.BattleModeConverter
import flashtanki.server.utils.LocalizedString
import flashtanki.server.utils.toLocalizedString

@Entity
@DiscriminatorValue("earn_score_mode")
class EarnScoreInModeQuest(
  id: Int,
  user: User,
  questIndex: Int,

  current: Int,
  required: Int,

  new: Boolean,
  completed: Boolean,

  rewards: MutableList<ServerDailyQuestReward>,

  @Convert(converter = BattleModeConverter::class)
  val mode: BattleMode,
  preview: Int = 123333
) : ServerDailyQuest(
  id, user, questIndex,
  current, required,
  new, completed,
  rewards, preview
) {
  override val description: LocalizedString
    get() = mapOf(
      SocketLocale.English to "Earn experience in $mode",
      SocketLocale.Russian to "Набери опыт в режиме $mode"
    ).toLocalizedString()
}
