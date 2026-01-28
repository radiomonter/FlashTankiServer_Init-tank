@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package flashtanki.server

import kotlinx.serialization.Serializable


@Serializable
data class PrizeType(
    val type: String,
    val quantity: Int
)

@Serializable
data class PromoCode(
    val code: String,
    val types: List<PrizeType>
)
