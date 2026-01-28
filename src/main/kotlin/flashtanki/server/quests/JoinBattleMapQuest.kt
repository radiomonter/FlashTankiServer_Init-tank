package flashtanki.server.quests

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import flashtanki.server.client.SocketLocale
import flashtanki.server.client.User
import flashtanki.server.utils.LocalizedString
import flashtanki.server.utils.toLocalizedString

@Entity
@DiscriminatorValue("join_battle_map")
class JoinBattleMapQuest(
  id: Int,
  user: User,
  questIndex: Int,

  current: Int,
  required: Int,

  new: Boolean,
  completed: Boolean,

  rewards: MutableList<ServerDailyQuestReward>,

  val map: String,
  preview: Int = 123333
) : ServerDailyQuest(
  id, user, questIndex,
  current, required,
  new, completed,
  rewards, preview
) {
  override val description: LocalizedString
    get() = mapOf(
      SocketLocale.English to "Join battles on $map map",
      SocketLocale.Russian to "Зайди в битвы на карте $map"
    ).toLocalizedString()
}
