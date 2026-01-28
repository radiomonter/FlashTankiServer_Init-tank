package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.battles.BattleTeam

class BattleTeamAdapter {
  @ToJson
  fun toJson(type: BattleTeam): String = type.key

  @FromJson
  fun fromJson(value: String) = BattleTeam.get(value)
}
