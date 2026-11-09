package br.com.voz99.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.voz99.data.FavoritePlace
import br.com.voz99.model.ChatLine
import br.com.voz99.model.RideDraft
import kotlinx.coroutines.launch

private val Amber = Color(0xFFB96C00)
private val Cream = Color(0xFFFFF4E1)
private val Ink = Color(0xFF18202A)
private val CanvasColor = Color(0xFFF7F8FA)

@Composable
fun Voz99Screen(
    state: AppUiState,
    snackbar: SnackbarHostState,
    isListening: Boolean,
    isHandsFree: Boolean,
    onListen: () -> Unit,
    onSend: (String) -> Unit,
    onReset: () -> Unit,
    onEditDraft: (String?, String?, String?) -> Unit,
    onLocation: () -> Unit,
    onOpen99: () -> Unit,
    onShowPin: () -> Unit,
    onStopPin: () -> Unit,
    onAccessibility: () -> Unit,
    onSavePreferences: (String, String, String) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onRemoveGeminiKey: () -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onRemoveFavorite: (FavoritePlace) -> Unit,
    onChooseFavorite: (FavoritePlace) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = CanvasColor,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White, shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Voz99", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                        Spacer(Modifier.weight(1f))
                        Text("PROTÓTIPO", fontSize = 11.sp, color = Amber, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (state.aiEnabled) "● Conversa com Gemini ativa" else "● Modo demonstração (sem IA)",
                        color = if (state.aiEnabled) Color(0xFF236E4A) else Amber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Conversa", "Favoritos", "Ajustes").forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(listOf("◎", "☆", "⚙")[index], fontSize = 23.sp) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            when (tab) {
                0 -> ConversationPage(
                    state, isListening, isHandsFree, onListen, onSend, onReset,
                    onEditDraft, onLocation, onOpen99, onShowPin
                )
                1 -> FavoritesPage(
                    favorites = state.favorites,
                    onAdd = onAddFavorite,
                    onDelete = onRemoveFavorite,
                    onChoose = { onChooseFavorite(it); tab = 0 }
                )
                else -> SettingsPage(state, onSavePreferences, onSaveGeminiKey, onRemoveGeminiKey, onAccessibility, onStopPin)
            }
        }
    }
}

@Composable
private fun ConversationPage(
    state: AppUiState,
    isListening: Boolean,
    isHandsFree: Boolean,
    onListen: () -> Unit,
    onSend: (String) -> Unit,
    onReset: () -> Unit,
    onEditDraft: (String?, String?, String?) -> Unit,
    onLocation: () -> Unit,
    onOpen99: () -> Unit,
    onShowPin: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Converse naturalmente", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Diga para onde quer ir ou pergunte sobre a 99.", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
        Spacer(Modifier.height(9.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages, key = { it.id }) { line -> Bubble(line) }
            if (state.isBusy) item { Text("A IA está pensando…", color = Amber, modifier = Modifier.padding(8.dp)) }
        }
        if (!state.aiEnabled) {
            Text(
                "Para ativar IA real: abra Ajustes e cole sua chave gratuita do Google AI Studio.",
                fontSize = 12.sp, color = Amber, modifier = Modifier.padding(vertical = 5.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(450) },
                label = { Text("Escreva ou use a voz") },
                placeholder = { Text("Ex.: quero ir à farmácia") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.isNotBlank()) { onSend(text); text = "" }
                }),
                enabled = !state.isBusy
            )
            Spacer(Modifier.width(7.dp))
            FilledTonalButton(
                onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
                modifier = Modifier.height(54.dp),
                enabled = !state.isBusy && text.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 15.dp)
            ) { Text("➤", fontSize = 22.sp) }
        }
        Spacer(Modifier.height(7.dp))
        Button(
            onClick = onListen,
            modifier = Modifier.fillMaxWidth().height(68.dp),
            enabled = !state.isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = if (isHandsFree) Color(0xFF9E3131) else Amber),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(if (isHandsFree) "■  Encerrar conversa" else "🎙  Iniciar conversa", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            if (isHandsFree) (if (isListening) "Ouvindo você…" else "Atendimento ativo — aguardando resposta…")
            else "Toque uma vez para começar; a voz se reativa após cada resposta.",
            fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 3.dp)
        )
        Spacer(Modifier.height(8.dp))
        DraftCard(state.draft, state.locationStatus, onEditDraft, onLocation, onOpen99, onShowPin)
        TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) { Text("Reiniciar conversa") }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (line.fromUser) Color(0xFFE5E9F0) else Color.White,
            border = if (line.fromUser) null else BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth(0.91f)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (line.fromUser) "VOCÊ" else "VOZ99", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Amber)
                Spacer(Modifier.height(3.dp))
                Text(line.text, fontSize = 16.sp, color = Ink, lineHeight = 23.sp)
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: RideDraft,
    locationStatus: String,
    onEdit: (String?, String?, String?) -> Unit,
    onLocation: () -> Unit,
    onOpen99: () -> Unit,
    onShowPin: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚗  Sua viagem", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher" else "Editar") }
            }
            Text("Origem: ${draft.origin.ifBlank { "a confirmar" }}", maxLines = 2, fontSize = 13.sp)
            Text("Destino: ${draft.destination.ifBlank { "a confirmar" }}", maxLines = 2, fontSize = 13.sp)
            Text("Pagamento: ${draft.payment}", fontSize = 13.sp)
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.origin,
                    onValueChange = { onEdit(it, null, null) },
                    label = { Text("Origem completa") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.destination,
                    onValueChange = { onEdit(null, it, null) },
                    label = { Text("Destino completo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.payment,
                    onValueChange = { onEdit(null, null, it) },
                    label = { Text("Preferência de pagamento") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = onLocation, modifier = Modifier.fillMaxWidth()) { Text("📍 Preencher origem por GPS") }
                Text(locationStatus, fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpen99, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir a 99 e concluir a corrida", textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onShowPin, modifier = Modifier.fillMaxWidth()) { Text("Destacar PIN na 99 (teste)") }
            Text(
                "A corrida não é solicitada por este aplicativo. Preço, destino, pagamento e confirmação são feitos na 99.",
                fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun FavoritesPage(
    favorites: List<FavoritePlace>,
    onAdd: (String, String) -> Unit,
    onDelete: (FavoritePlace) -> Unit,
    onChoose: (FavoritePlace) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Destinos favoritos", fontSize = 23.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text("Cadastre os lugares mais usados pelos familiares.", fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Apelido (ex.: Farmácia)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(value = address, onValueChange = { address = it.take(250) }, label = { Text("Endereço completo") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(9.dp))
        Button(
            onClick = { onAdd(name, address); name = ""; address = "" },
            enabled = name.isNotBlank() && address.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("Salvar favorito") }
        Spacer(Modifier.height(12.dp))
        if (favorites.isEmpty()) Text("Nenhum destino cadastrado.", color = Color.Gray)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favorites, key = { it.id }) { place ->
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onChoose(place) }) {
                            Text("☆ ${place.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(place.address, fontSize = 13.sp, color = Color.DarkGray)
                            Text("Toque para usar como destino", fontSize = 11.sp, color = Amber)
                        }
                        TextButton(onClick = { onDelete(place) }) { Text("Excluir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: AppUiState,
    onSave: (String, String, String) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onRemoveGeminiKey: () -> Unit,
    onAccessibility: () -> Unit,
    onStopPin: () -> Unit
) {
    var name by remember(state.name) { mutableStateOf(state.name) }
    var home by remember(state.home) { mutableStateOf(state.home) }
    var payment by remember(state.payment) { mutableStateOf(state.payment) }
    var keyDraft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Ajustes do familiar", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item {
                Text("Inteligência artificial (sem Firebase)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (state.aiEnabled) "Gemini configurado neste aparelho." else "Ainda não configurado: modo demonstração.", fontSize = 13.sp)
                Text("Entre no Google AI Studio, crie uma chave gratuita e cole abaixo. Ela será criptografada no celular com Android Keystore. Não compartilhe sua chave.", fontSize = 13.sp)
            }
            item {
                OutlinedTextField(
                    keyDraft, { keyDraft = it.take(1000) },
                    label = { Text("Chave da API Gemini") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = { onSaveGeminiKey(keyDraft); keyDraft = "" },
                    enabled = keyDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()
                ) { Text("Ativar IA neste celular") }
            }
            if (state.aiEnabled) item {
                OutlinedButton(onClick = onRemoveGeminiKey, modifier = Modifier.fillMaxWidth()) {
                    Text("Remover chave Gemini do celular")
                }
            }
            item { HorizontalDivider() }
            item {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text("Como a pessoa gosta de ser chamada") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(home, { home = it.take(250) }, label = { Text("Endereço de casa (completo)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
            item {
                OutlinedTextField(payment, { payment = it.take(60) }, label = { Text("Pagamento preferido") }, modifier = Modifier.fillMaxWidth())
            }
            item { Button(onClick = { onSave(name, home, payment) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar preferências") } }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(9.dp))
                Text("Destaque de PIN", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Para funcionar, ative manualmente 'Voz99 • destacar PIN da 99' nas configurações de acessibilidade. O serviço só analisa a janela da 99 quando solicitado e por até dois minutos.", fontSize = 13.sp)
            }
            item { OutlinedButton(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Abrir configurações de acessibilidade") } }
            item { OutlinedButton(onClick = onStopPin, modifier = Modifier.fillMaxWidth()) { Text("Desativar destaque do PIN") } }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Privacidade e limites", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "• Com chave configurada, a conversa, nomes e endereços informados são enviados à API Gemini.\n" +
                    "• Favoritos ficam armazenados apenas neste aparelho.\n" +
                    "• Não armazenamos o PIN e não solicitamos corridas automaticamente.\n" +
                    "• Sem chave Gemini, funciona apenas a demonstração local.\n" +
                    "• Nunca compartilhe sua chave; este uso direto da API serve somente para testes privados.\n" +
                    "• O reconhecimento por voz pode usar a internet, dependendo do Android.",
                    fontSize = 13.sp, lineHeight = 20.sp
                )
            }
        }
    }
}
