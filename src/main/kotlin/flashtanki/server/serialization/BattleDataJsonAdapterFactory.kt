package flashtanki.server.serialization

import java.lang.reflect.Type
import com.squareup.moshi.*
import flashtanki.server.client.BattleData
import flashtanki.server.client.DmBattleData
import flashtanki.server.client.TeamBattleData

class BattleDataJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    if(type.rawType == BattleData::class.java) {
      return object : JsonAdapter<BattleData>() {
        override fun fromJson(reader: JsonReader): BattleData? {
          TODO("Not yet implemented")
        }

        override fun toJson(writer: JsonWriter, value: BattleData?) {
          when(value) {
            null              -> writer.nullValue()
            is DmBattleData   -> moshi.adapter(DmBattleData::class.java).toJson(writer, value)
            is TeamBattleData -> moshi.adapter(TeamBattleData::class.java).toJson(writer, value)
            else              -> throw IllegalArgumentException("Unknown battle data type: ${value::class}")
          }
        }
      }
    }
    return null
  }
}
