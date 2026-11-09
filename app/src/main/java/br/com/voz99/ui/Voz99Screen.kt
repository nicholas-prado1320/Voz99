package br.com.voz99.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.voz99.R
import br.com.voz99.data.FavoritePlace

private val Amber = Color(0xFFB96C00)
private val Ink = Color(0xFF18202A)
private val CanvasColor = Color(0xFFF7F8FA)
private val Inactive = Color(0xFF6F7784)

@Composable
fun Voz99Screen(
    state: AppUiState,
    snackbar: SnackbarHostState,
    isListening: Boolean,
    isHandsFree: Boolean,
    onListen: () -> Unit,
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
        bottomBar = {
            CompactBottomBar(selectedTab = tab, onTabSelected = { tab = it })
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> MicrophoneHomePage(
                    isListening = isListening,
                    isHandsFree = isHandsFree,
                    isBusy = state.isBusy,
                    onListen = onListen
                )
                1 -> FavoritesPage(
                    favorites = state.favorites,
                    onAdd = onAddFavorite,
                    onDelete = onRemoveFavorite,
                    onChoose = { onChooseFavorite(it); tab = 0 }
                )
                else -> SettingsPage(
                    state = state,
                    onSave = onSavePreferences,
                    onSaveGeminiKey = onSaveGeminiKey,
                    onRemoveGeminiKey = onRemoveGeminiKey
                )
            }
        }
    }
}

/** Tela inicial propositalmente simples: apenas o microfone e o estado da conversa. */
@Composable
private fun MicrophoneHomePage(
    isListening: Boolean,
    isHandsFree: Boolean,
    isBusy: Boolean,
    onListen: () -> Unit
) {
    val status = when {
        isBusy -> "Preparando resposta…"
        isListening -> "Estou ouvindo você…"
        isHandsFree -> "Conversa ativa"
        else -> "Toque no microfone para conversar"
    }
    val canTap = !isBusy || isHandsFree
    val buttonColor = if (isHandsFree) Color(0xFF9A3636) else Amber
    val actionDescription = if (isHandsFree) "Encerrar conversa por voz" else "Iniciar conversa por voz"

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier.size(220.dp)
                    .background(buttonColor.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(178.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(buttonColor)
                        .clickable(enabled = canTap, onClick = onListen)
                        .semantics { contentDescription = actionDescription },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (isHandsFree) R.drawable.ic_stop_voice else R.drawable.ic_microphone
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(83.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = status,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Menu preservado no rodapé, mas com altura reduzida e ícones vetoriais reais. */
@Composable
private fun CompactBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val entries = listOf(
        Triple("Conversa", R.drawable.ic_chat_nav, 0),
        Triple("Favoritos", R.drawable.ic_favorite_nav, 1),
        Triple("Ajustes", R.drawable.ic_settings_nav, 2)
    )
    Surface(color = Color.White, shadowElevation = 3.dp) {
        Column {
            HorizontalDivider(color = Color(0xFFE8E9ED), thickness = 1.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                entries.forEach { (label, icon, index) ->
                    val selected = selectedTab == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 5.dp)
                            .semantics { contentDescription = "Abrir $label" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                            tint = if (selected) Amber else Inactive
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            color = if (selected) Amber else Inactive,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
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
                Text("Integração com a 99", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Nesta versão de teste, a IA pode abrir o aplicativo 99, mas não destaca o PIN nem solicita corridas automaticamente. O recurso de acessibilidade foi removido para testar voz e Gemini com menos permissões.",
                    fontSize = 13.sp
                )
            }
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