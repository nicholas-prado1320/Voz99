package br.com.voz99.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.voz99.ai.DemoConversation
import br.com.voz99.ai.GeminiConversation
import br.com.voz99.data.FavoritePlace
import br.com.voz99.data.FavoritesDatabase
import br.com.voz99.data.GeminiKeyStore
import br.com.voz99.data.UserPreferences
import br.com.voz99.location.LocationHelper
import br.com.voz99.model.AiAction
import br.com.voz99.model.ChatLine
import br.com.voz99.model.RideDraft
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

sealed interface UiEffect {
    data class Action(val type: AiAction) : UiEffect
    data class Message(val value: String) : UiEffect
}

data class AppUiState(
    val messages: List<ChatLine> = listOf(ChatLine(1, false, "Olá! Eu posso ajudar você a preparar uma corrida na 99. Para onde deseja ir?")),
    val draft: RideDraft = RideDraft(),
    val favorites: List<FavoritePlace> = emptyList(),
    val isBusy: Boolean = false,
    val aiEnabled: Boolean = false,
    val lastReplyId: Long = 0,
    val home: String = "",
    val name: String = "",
    val payment: String = "Dinheiro",
    val locationStatus: String = "Localização não consultada"
)

class MainViewModel(private val application: Application) : ViewModel() {
    private val db = FavoritesDatabase.get(application)
    private val prefs = UserPreferences(application)
    private val keyStore = GeminiKeyStore(application)
    private val model = GeminiConversation(application)
    private val location = LocationHelper(application)
    private val ids = AtomicLong(1)
    private val _state = MutableStateFlow(AppUiState(
        aiEnabled = model.isReady,
        home = prefs.homeAddress,
        name = prefs.userName,
        payment = prefs.defaultPayment,
        draft = RideDraft(payment = prefs.defaultPayment)
    ))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            db.favoriteDao().observeAll().collect { found ->
                _state.update { it.copy(favorites = found) }
            }
        }
    }

    fun send(text: String) {
        val clean = text.trim().take(450)
        if (clean.isBlank() || _state.value.isBusy) return
        val userLine = ChatLine(ids.incrementAndGet(), true, clean)
        _state.update { it.copy(messages = it.messages + userLine, isBusy = true) }
        viewModelScope.launch {
            val snapshot = _state.value
            val result = try {
                if (snapshot.aiEnabled) {
                    model.respond(snapshot.messages, snapshot.draft, snapshot.favorites,
                        snapshot.home, snapshot.payment, snapshot.name)
                } else DemoConversation.respond(clean, snapshot.draft)
            } catch (_: Exception) {
                // Não afirma ter executado qualquer operação em caso de falha.
                appendAssistant("Não consegui acessar a IA agora. Confira sua conexão e a chave Gemini em Ajustes. Você ainda pode abrir a 99 pelo botão abaixo.")
                _effects.send(UiEffect.Message("Falha ao consultar Gemini. Nenhuma corrida foi solicitada."))
                _state.update { it.copy(isBusy = false) }
                return@launch
            }
            _state.update { previous ->
                previous.copy(draft = previous.draft.copy(
                    origin = result.origin ?: previous.draft.origin,
                    destination = result.destination ?: previous.draft.destination,
                    payment = result.payment ?: previous.draft.payment
                ))
            }
            appendAssistant(result.speech.ifBlank { "Como posso ajudar?" })
            _state.update { it.copy(isBusy = false) }
            if (result.action != AiAction.NONE) _effects.send(UiEffect.Action(result.action))
        }
    }

    private fun appendAssistant(text: String) {
        val line = ChatLine(ids.incrementAndGet(), false, text)
        _state.update { it.copy(messages = it.messages + line, lastReplyId = line.id) }
    }

    fun editDraft(origin: String? = null, destination: String? = null, payment: String? = null) {
        _state.update { prev -> prev.copy(draft = prev.draft.copy(
            origin = origin ?: prev.draft.origin,
            destination = destination ?: prev.draft.destination,
            payment = payment ?: prev.draft.payment
        )) }
    }

    fun clearConversation() {
        _state.update { it.copy(
            messages = listOf(ChatLine(ids.incrementAndGet(), false, "Vamos começar de novo. Qual é o seu destino?")),
            draft = RideDraft(payment = it.payment), lastReplyId = 0
        ) }
    }

    fun saveGeminiKey(key: String) {
        if (keyStore.saveKey(key)) {
            _state.update { it.copy(aiEnabled = true) }
            viewModelScope.launch { _effects.send(UiEffect.Message("Gemini ativado neste aparelho.")) }
        } else {
            viewModelScope.launch { _effects.send(UiEffect.Message("Não foi possível proteger a chave. Confira o valor e tente novamente.")) }
        }
    }

    fun removeGeminiKey() {
        keyStore.removeKey()
        _state.update { it.copy(aiEnabled = false) }
        viewModelScope.launch { _effects.send(UiEffect.Message("Chave apagada deste aparelho. Modo demonstração ativado.")) }
    }

    fun updatePreferences(name: String, home: String, payment: String) {
        prefs.userName = name
        prefs.homeAddress = home
        prefs.defaultPayment = payment
        _state.update { it.copy(name = prefs.userName, home = prefs.homeAddress, payment = prefs.defaultPayment) }
        viewModelScope.launch { _effects.send(UiEffect.Message("Preferências salvas neste celular.")) }
    }

    fun addFavorite(name: String, address: String) {
        if (name.isBlank() || address.isBlank()) return
        viewModelScope.launch {
            db.favoriteDao().insert(FavoritePlace(name = name.trim().take(60), address = address.trim().take(250)))
            _effects.send(UiEffect.Message("Destino favorito salvo."))
        }
    }

    fun deleteFavorite(place: FavoritePlace) {
        viewModelScope.launch { db.favoriteDao().delete(place) }
    }

    fun chooseFavorite(place: FavoritePlace) {
        editDraft(destination = place.address)
        viewModelScope.launch { _effects.send(UiEffect.Message("Destino selecionado: ${place.name}")) }
    }

    fun requestLocation() {
        viewModelScope.launch {
            if (!location.hasPermission()) {
                _effects.send(UiEffect.Message("Permissão de localização necessária."))
                return@launch
            }
            _state.update { it.copy(locationStatus = "Localizando...") }
            val address = location.currentAddress()
            if (address == null) {
                _state.update { it.copy(locationStatus = "Não foi possível obter a localização. Ligue o GPS.") }
            } else {
                _state.update { it.copy(locationStatus = address, draft = it.draft.copy(origin = address)) }
                _effects.send(UiEffect.Message("Origem aproximada preenchida. Confira antes de usar."))
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(application) as T
    }
}
