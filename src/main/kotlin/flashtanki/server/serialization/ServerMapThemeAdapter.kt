package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.ServerMapTheme

class ServerMapThemeAdapter {
  @ToJson
  fun toJson(type: ServerMapTheme): String = type.key

  @FromJson
  fun fromJson(value: String) = ServerMapTheme.get(value)
}
