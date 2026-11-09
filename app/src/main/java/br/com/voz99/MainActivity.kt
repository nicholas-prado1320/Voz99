package br.com.voz99

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.voz99.accessibility.PinHighlightService
import br.com.voz99.model.AiAction
import br.com.voz99.platform.NinetyNineLauncher
import br.com.voz99.ui.UiEffect
import br.com.voz99.ui.Voz99Screen
import br.com.voz99.ui.MainViewModel
import br.com.voz99.voice.AndroidSpeech
import br.com.voz99.voice.AndroidVoiceOutput
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, MainViewModel.Factory(application))[MainViewModel::class.java]
        setContent {
            val context = LocalContext.current
            val state by vm.state.collectAsState()
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var listening by remember { mutableStateOf(false) }
            var handsFree by remember { mutableStateOf(false) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val speech = remember { AndroidSpeech(context) }
            val speaker = remember { AndroidVoiceOutput(context) }
            var micError by remember { mutableStateOf("") }

            DisposableEffect(speech, speaker) {
                speech.onResult = { vm.send(it) }
                speech.onError = { message -> micError = message; handsFree = false }
                speech.onListening = { listening = it }
                speaker.onDone = { if (handsFree) speech.listen() }
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        handsFree = false
                        speech.stop()
                        speaker.stop()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer); speech.destroy(); speaker.shutdown() }
            }
            LaunchedEffect(micError) {
                if (micError.isNotBlank()) {
                    snackbar.showSnackbar(micError)
                    micError = ""
                }
            }
            LaunchedEffect(state.lastReplyId) {
                if (state.lastReplyId > 0) {
                    state.messages.lastOrNull { it.id == state.lastReplyId }?.text?.let(speaker::say)
                }
            }
            val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) { handsFree = true; speaker.stop(); speech.listen() }
                else scope.launch { snackbar.showSnackbar("Autorize o microfone para falar com o aplicativo.") }
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
            LaunchedEffect(vm) {
                vm.effects.collect { effect ->
                    when (effect) {
                        is UiEffect.Message -> snackbar.showSnackbar(effect.value)
                        is UiEffect.Action -> when (effect.type) {
                            AiAction.OPEN_99 -> NinetyNineLauncher.launch(context)
                            AiAction.SHOW_PIN -> {
                                PinHighlightService.arm(context)
                                scope.launch { snackbar.showSnackbar("Destaque do PIN ativado por 2 minutos, se o código estiver acessível.") }
                                NinetyNineLauncher.launch(context)
                            }
                            AiAction.FIND_LOCATION -> requestLocation()
                            AiAction.NONE -> Unit
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
                            speaker.stop()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            handsFree = true
                            speaker.stop()
                            speech.listen()
                        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onSend = vm::send,
                    onReset = vm::clearConversation,
                    onEditDraft = vm::editDraft,
                    onLocation = ::requestLocation,
                    onOpen99 = { NinetyNineLauncher.launch(context) },
                    onShowPin = {
                        PinHighlightService.arm(context)
                        scope.launch { snackbar.showSnackbar("Destaque temporário ativado; exige Serviço de Acessibilidade habilitado.") }
                        NinetyNineLauncher.launch(context)
                    },
                    onStopPin = {
                        PinHighlightService.disarm(context)
                        scope.launch { snackbar.showSnackbar("Destaque desativado.") }
                    },
                    onAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
