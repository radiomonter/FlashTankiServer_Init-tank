package flashtanki.server.quests

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import flashtanki.server.client.SocketLocale
import flashtanki.server.client.User
import flashtanki.server.utils.LocalizedString
import flashtanki.server.utils.toLocalizedString

@Entity
@DiscriminatorValue("deliver_flag")
class DeliverFlagQuest(
  id: Int,
  user: User,
  questIndex: Int,

  current: Int,
  required: Int,

  new: Boolean,
  completed: Boolean,

  rewards: MutableList<ServerDailyQuestReward>,
  preview: Int = 123336
) : ServerDailyQuest(
  id, user, questIndex,
  current, required,
  new, completed,
  rewards, preview
) {
  override val description: LocalizedString
    get() = mapOf(
      SocketLocale.English to "Deliver the flags",
      SocketLocale.Russian to "Доставь флаги"
    ).toLocalizedString()
}
