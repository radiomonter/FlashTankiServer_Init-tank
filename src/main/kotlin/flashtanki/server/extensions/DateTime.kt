package flashtanki.server.extensions

import kotlinx.datetime.*

fun String.complementLeft(complementUpto: Int, complement: String) =
  if(length >= complementUpto) this
  else (complementUpto - length).let { delta ->
    complement.repeat(delta / complement.length + (if(delta % complement.length != 0) 1 else 0)) + this
  }

@Suppress("PropertyName", "unused", "SpellCheckingInspection")
open class KotlinDateFormat(private val date: LocalDate) {
  val yyyy by lazy { date.year format 4 }
  val yy by lazy { date.year format 2 }
  val DDD by lazy { date.dayOfYear format 3 }
  val MM by lazy { date.monthNumber format 2 }
  val LL by lazy { date.month.name }
  val LLLL by lazy { date.month.name }
  val EE by lazy { date.dayOfWeek.name }
  val dd by lazy { date.dayOfMonth format 2 }
  protected infix fun Number.format(count: Int) = toString().run {
    if(length < count) complementLeft(count, "0")
    else if(length > count) substring((length - count) until length)
    else this
  }
}

val LocalDateTime.isPM get() = hour >= 12
val LocalDateTime.isAM get() = !isPM
val LocalDateTime.ampm get() = if(isAM) "am" else "pm"
val LocalDateTime.AMPM get() = if(isAM) "AM" else "PM"

@Suppress("PropertyName", "unused", "SpellCheckingInspection")
open class KotlinDateTimeFormat(private val time: LocalDateTime) : KotlinDateFormat(time.date) {
  val aa by lazy { time.ampm }
  val AA by lazy { time.AMPM }
  val hh by lazy { (time.hour % 12) format 2 }
  val HH by lazy { time.hour format 2 }
  val mm by lazy { time.minute format 2 }
  val ss by lazy { time.second format 2 }
  val nn by lazy { time.nanosecond format 9 }
}

fun LocalDate.toString(format: KotlinDateFormat.() -> String): String = KotlinDateFormat(this).format()

fun LocalDateTime.toString(format: KotlinDateTimeFormat.() -> String): String = KotlinDateTimeFormat(this).format()

fun Instant.toString(
  timeZone: TimeZone = TimeZone.currentSystemDefault(),
  format: KotlinDateTimeFormat.() -> String
): String = KotlinDateTimeFormat(this.toLocalDateTime(timeZone)).format()
