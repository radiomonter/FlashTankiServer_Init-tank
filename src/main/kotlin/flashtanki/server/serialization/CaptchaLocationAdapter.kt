package flashtanki.server.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import flashtanki.server.client.CaptchaLocation

class CaptchaLocationAdapter {
  @ToJson
  fun toJson(type: CaptchaLocation): String = type.key

  @FromJson
  fun fromJson(value: String) = CaptchaLocation.get(value)
}
