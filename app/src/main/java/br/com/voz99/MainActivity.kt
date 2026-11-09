package br.com.voz99

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.voz99.model.AiAction
import br.com.voz99.platform.NinetyNineLauncher
import br.com.voz99.ui.MainViewModel
import br.com.voz99.ui.UiEffect
import br.com.voz99.ui.Voz99Screen
import br.com.voz99.voice.AndroidSpeech
import br.com.voz99.voice.AndroidVoiceOutput
import br.com.voz99.voice.VoiceOverlayService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, MainViewModel.Factory(application))[MainViewModel::class.java]
        setContent {
            val context = LocalContext.current
            val state by vm.state.collectAsState()
            val latestState by rememberUpdatedState(state)
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var listening by remember { mutableStateOf(false) }
            var handsFree by remember { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val speech = remember { AndroidSpeech(context) }
            val speaker = remember { AndroidVoiceOutput(context) }
            var micError by remember { mutableStateOf("") }

            DisposableEffect(speech, speaker, lifecycleOwner) {
                speech.onResult = { vm.send(it) }
                speech.onError = { message -> micError = message; handsFree = false }
                speech.onListening = { listening = it }
                // Para respostas comuns, reativa o microfone depois da fala.
                speaker.onDone = { if (handsFree) speech.listen() }
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        handsFree = false
                        speech.stop()
                        speaker.stop()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    speech.destroy()
                    speaker.shutdown()
                }
            }
            LaunchedEffect(micError) {
                if (micError.isNotBlank()) {
                    snackbar.showSnackbar(micError)
                    micError = ""
                }
            }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) {
                    handsFree = true
                    speaker.stop()
                    speech.listen()
                } else scope.launch { snackbar.showSnackbar("Autorize o microfone para falar com o aplicativo.") }
            }
            val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                if (grants.values.any { it }) vm.requestLocation()
                else scope.launch { snackbar.showSnackbar("Localização não autorizada.") }
            }
            fun requestLocation() {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) vm.requestLocation()
                else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            LaunchedEffect(vm, speaker, speech) {
                vm.effects.collect { effect ->
                    when (effect) {
                        is UiEffect.Message -> snackbar.showSnackbar(effect.value)
                        is UiEffect.SpokenReply -> {
                            // Se desejado, iniciar o serviço com a tela visível. A abertura da 99 independe dele.
                            // Iniciar após abrir a 99 pode gerar SecurityException no Android 14+.
                            val opening99 = effect.action == AiAction.OPEN_99 || effect.action == AiAction.SHOW_PIN
                            val overlayAllowed = Settings.canDrawOverlays(context)
                            val micAllowed = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            val serviceStarted = if (opening99 && overlayAllowed && micAllowed) {
                                runCatching {
                                    val intent = Intent(context, VoiceOverlayService::class.java).apply {
                                        putExtra(
                                            VoiceOverlayService.EXTRA_CONTEXT,
                                            VoiceOverlayService.buildContext(
                                                latestState.messages,
                                                latestState.draft
                                            )
                                        )
                                    }
                                    ContextCompat.startForegroundService(context, intent)
                                }.onFailure { error ->
                                    Log.e("Voz99", "Falha ao iniciar serviço flutuante", error)
                                }.isSuccess
                            } else false

                            // A fala sempre termina ANTES de trocar o aplicativo.
                            val spokenText = when {
                                opening99 && !overlayAllowed ->
                                    "${effect.text} Vou abrir a 99, mas a bolha de voz ficará desativada. " +
                                            "Para usá-la, autorize aparecer sobre outros aplicativos nas configurações do Voz99."
                                opening99 && !micAllowed ->
                                    "${effect.text} Vou abrir a 99 sem o atendimento por voz em segundo plano. " +
                                            "Para ativá-lo, autorize o microfone do Voz99."
                                opening99 && !serviceStarted ->
                                    "${effect.text} Vou abrir a 99. O botão flutuante não pôde ser ativado."
                                else -> effect.text
                            }
                            speaker.say(spokenText) { finished ->
                                if (!finished) {
                                    handsFree = false
                                    if (serviceStarted) context.stopService(
                                        Intent(context, VoiceOverlayService::class.java)
                                    )
                                    scope.launch {
                                        snackbar.showSnackbar("Não consegui concluir a instrução por voz.")
                                    }
                                } else when (effect.action) {
                                    AiAction.OPEN_99, AiAction.SHOW_PIN -> {
                                        handsFree = false
                                        speech.stop()
                                        // Abrir a 99 NÃO depende de microfone ou bolha.
                                        // O serviço flutuante é opcional; sua falha não impede a navegação.
                                        if (effect.action == AiAction.SHOW_PIN) {
                                            Toast.makeText(
                                                context,
                                                "Na 99, abra a corrida e localize o PIN. A leitura automática ainda não existe.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        if (!serviceStarted) {
                                            Toast.makeText(
                                                context,
                                                "A 99 será aberta sem a bolha de voz. Verifique as permissões de microfone e sobreposição.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        NinetyNineLauncher.launch(context)
                                    }
                                    AiAction.FIND_LOCATION -> {
                                        handsFree = false
                                        requestLocation()
                                    }
                                    AiAction.NONE -> if (handsFree) speech.listen()
                                }
                            }
                        }
                    }
                }
            }
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFAB6500), secondary = Color(0xFF795548))) {
                Voz99Screen(
                    state = state,
                    snackbar = snackbar,
                    isListening = listening,
                    isHandsFree = handsFree,
                    onListen = {
                        if (handsFree) {
                            handsFree = false
                            speech.stop()
                            speaker.stop() // Cancela qualquer ação que aguardava o fim da fala.
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            handsFree = true
                            speaker.stop()
                            speech.listen()
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSavePreferences = vm::updatePreferences,
                    onSaveGeminiKey = vm::saveGeminiKey,
                    onRemoveGeminiKey = vm::removeGeminiKey,
                    onAddFavorite = vm::addFavorite,
                    onRemoveFavorite = vm::deleteFavorite,
                    onChooseFavorite = vm::chooseFavorite
                )
            }
        }
    }
}