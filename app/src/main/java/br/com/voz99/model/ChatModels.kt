package br.com.voz99.model

data class ChatLine(val id: Long, val fromUser: Boolean, val text: String)

data class RideDraft(
    val origin: String = "",
    val destination: String = "",
    val payment: String = "Não informado"
)

enum class AiAction { NONE, OPEN_99, SHOW_PIN, FIND_LOCATION }

data class AiAnswer(
    val speech: String,
    val action: AiAction = AiAction.NONE,
    val origin: String? = null,
    val destination: String? = null,
    val payment: String? = null
)

object AiActionParser {
    fun parse(value: String?): AiAction = when (value?.trim()?.uppercase()) {
        "OPEN_99" -> AiAction.OPEN_99
        "SHOW_PIN" -> AiAction.SHOW_PIN
        "FIND_LOCATION" -> AiAction.FIND_LOCATION
        else -> AiAction.NONE
    }
}
