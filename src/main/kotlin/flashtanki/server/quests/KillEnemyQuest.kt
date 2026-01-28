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
@DiscriminatorValue("kill_enemy")
class KillEnemyQuest(
  id: Int,
  user: User,
  questIndex: Int,

  current: Int,
  required: Int,

  new: Boolean,
  completed: Boolean,

  rewards: MutableList<ServerDailyQuestReward>,

  @Convert(converter = BattleModeConverter::class)
  val mode: BattleMode?,
  preview: Int = 123333
) : ServerDailyQuest(
  id, user, questIndex,
  current, required,
  new, completed,
  rewards, preview
) {
  // TODO(Assasans): Localize mode name
  override val description: LocalizedString
    get() = mapOf(
      SocketLocale.English to if(mode != null) "Kill enemy tanks in ${mode!!.name} mode" else "Kill enemy tanks",
      SocketLocale.Russian to if(mode != null) "Уничтожь противников в режиме ${mode!!.name}" else "Уничтожь противников"
    ).toLocalizedString()
}
