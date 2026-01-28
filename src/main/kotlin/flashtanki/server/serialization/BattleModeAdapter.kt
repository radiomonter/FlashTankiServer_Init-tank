package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.battles.BattleMode

class BattleModeAdapter {
  @ToJson
  fun toJson(type: BattleMode): String = type.key

  @FromJson
  fun fromJson(value: String) = BattleMode.get(value)
}
