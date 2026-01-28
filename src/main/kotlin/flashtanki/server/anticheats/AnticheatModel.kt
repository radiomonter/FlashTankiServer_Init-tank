package flashtanki.server.anticheats

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnticheatModel(
    val name: String,
    val actionInfo: String
)
