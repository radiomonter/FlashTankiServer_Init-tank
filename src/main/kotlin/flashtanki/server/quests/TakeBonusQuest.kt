package flashtanki.server.quests

import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import flashtanki.server.BonusType
import flashtanki.server.client.SocketLocale
import flashtanki.server.client.User
import flashtanki.server.serialization.database.BonusTypeConverter
import flashtanki.server.utils.LocalizedString
import flashtanki.server.utils.toLocalizedString

@Entity
@DiscriminatorValue("take_bonus")
class TakeBonusQuest(
  id: Int,
  user: User,
  questIndex: Int,

  current: Int,
  required: Int,

  new: Boolean,
  completed: Boolean,

  rewards: MutableList<ServerDailyQuestReward>,
  @Convert(converter = BonusTypeConverter::class)
  val bonus: BonusType,
  preview: Int = 123337
) : ServerDailyQuest(
  id, user, questIndex,
  current, required,
  new, completed,
  rewards, preview
) {
  // TODO(Assasans): Localize bonus name
  override val description: LocalizedString
    get() = mapOf(
      SocketLocale.English to "Take ${bonus.name}",
      SocketLocale.Russian to "Подбери ${bonus.name}"
    ).toLocalizedString()
}
