package br.com.voz99.ai

import br.com.voz99.model.AiAction
import br.com.voz99.model.AiAnswer
import br.com.voz99.model.RideDraft
import java.text.Normalizer

/** Sem chave Gemini, este modo apenas demonstra o fluxo. Não é uma IA. */
object DemoConversation {
    fun respond(userText: String, ride: RideDraft): AiAnswer {
        val t = Normalizer.normalize(userText.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return when {
            Regex("\\b(pin|codigo|codig[o]? de seguranca)\\b").containsMatchIn(t) -> AiAnswer(
                "Posso abrir a 99 e tentar destacar o código. Confira se é o código da sua corrida.",
                AiAction.SHOW_PIN
            )
            "onde estou" in t || "localizacao" in t -> AiAnswer(
                "Vou tentar descobrir seu endereço atual pelo celular.", AiAction.FIND_LOCATION
            )
            "abrir" in t && "99" in t || "pode abrir" in t -> AiAnswer(
                "Vou abrir a 99. A solicitação e o preço devem ser confirmados no aplicativo oficial.",
                AiAction.OPEN_99
            )
            "dinheiro" in t -> AiAnswer("Certo, registrei dinheiro como preferência. Qual é o destino?", payment = "Dinheiro")
            "pix" in t -> AiAnswer("Certo. A disponibilidade de Pix depende das opções na 99. Para onde você vai?", payment = "Pix")
            "cartao" in t -> AiAnswer("Certo, registrei cartão como preferência. Para onde você vai?", payment = "Cartão")
            "vou para" in t || "quero ir" in t || "preciso ir" in t -> AiAnswer(
                "Entendi. No modo de demonstração eu não consigo validar o destino. Digite o endereço completo no cartão da viagem, ou configure o Gemini para conversar naturalmente."
            )
            "chamar" in t || "corrida" in t || "carro" in t -> AiAnswer(
                "Posso ajudar a preparar sua viagem. Você pode dizer o destino ou preencher os endereços abaixo. A corrida será concluída na 99."
            )
            ride.destination.isNotBlank() -> AiAnswer(
                "Seu destino provisório é ${ride.destination}. Quando estiver pronto, diga 'abrir a 99' e conclua a corrida lá."
            )
            else -> AiAnswer("Estou no modo de demonstração, sem IA. Posso ajudar com localização, pagamentos, abertura da 99 e destaque do PIN.")
        }
    }
}
