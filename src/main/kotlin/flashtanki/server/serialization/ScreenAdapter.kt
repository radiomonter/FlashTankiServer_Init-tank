package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.client.Screen

class ScreenAdapter {
  @ToJson
  fun toJson(type: Screen): String = type.key

  @FromJson
  fun fromJson(value: String) = Screen.get(value)
}
