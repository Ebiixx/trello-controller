package com.example.trellocontroller

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // Import für viewModels delegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier // <-- HIER IMPORT HINZUFÜGEN
import com.example.trellocontroller.utils.buildUiConfirmationText
import com.example.trellocontroller.ui.MainScreen
import com.example.trellocontroller.ui.theme.TrelloControllerTheme
import java.util.*


enum class ControllerState {
    WaitingForInitialCommand,
    // AddList Flow States werden durch AddListFlow.kt gehandhabt (indirekt über ViewModel)
    // AddCard Flow States werden durch AddCardFlow.kt gehandhabt (indirekt über ViewModel)
    // RenameCard Flow States werden durch RenameCardFlow.kt gehandhabt (indirekt über ViewModel)
    WaitingForConfirmation,
    ExecutingAction,
    Finished
}

class MainActivity : ComponentActivity(), MainViewModelActions {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private val utteranceCallbacks = mutableMapOf<String, (() -> Unit)?>()

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setViewModelActions(this) // ViewModel mit Activity-Aktionen verbinden

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.GERMAN
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        val callback = utteranceCallbacks.remove(utteranceId)
                        runOnUiThread { // Sicherstellen, dass Callbacks auf dem UI-Thread laufen
                            callback?.invoke()
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        utteranceCallbacks.remove(utteranceId) // Callback auch bei Fehler entfernen
                    }
                })
            } else {
                // TODO: Fehler bei der TTS-Initialisierung behandeln
            }
        }

        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0)
                viewModel.handleSpeechRecognitionResult(spoken)
            } else {
                viewModel.handleSpeechRecognitionError()
            }
        }

        setContent {
            TrelloControllerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Zustände aus dem ViewModel beobachten
                    val currentGlobalState = viewModel.currentGlobalState
                    val spokenText = viewModel.spokenText
                    val actionJsonForUi = viewModel.actionJsonForUi // Wird für buildUiConfirmationText benötigt
                    val currentFlowContextText = viewModel.currentFlowContextText
                    val trelloResult = viewModel.trelloResult

                    val contextualInfo = if (currentFlowContextText.isNotBlank()) {
                        currentFlowContextText
                    } else if (actionJsonForUi != null && currentGlobalState == ControllerState.WaitingForConfirmation) {
                        buildUiConfirmationText(actionJsonForUi) // buildUiConfirmationText benötigt JSONObject
                    } else {
                        ""
                    }

                    MainScreen(
                        statusText = currentGlobalState.name,
                        spokenText = spokenText,
                        contextualInfoText = contextualInfo,
                        trelloResultText = trelloResult.value, // <--- Änderung hier
                        onStartSpeechOrResetClick = {
                            if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
                                textToSpeech.stop()
                                utteranceCallbacks.clear() // Alle anstehenden Callbacks löschen
                            }
                            viewModel.onStartSpeechOrReset()
                        }
                    )
                }
            }
        }
    }

    // Implementierung von MainViewModelActions
    override fun speakWithCallback(text: String, onDone: (() -> Unit)?) {
        if (::textToSpeech.isInitialized) {
            val utteranceId = UUID.randomUUID().toString()
            utteranceCallbacks[utteranceId] = onDone
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        } else {
            // TODO: Behandeln, falls TTS nicht bereit ist (z.B. Nachricht in Log oder UI)
            onDone?.invoke() // Ggf. onDone direkt aufrufen, wenn TTS nicht geht
        }
    }

    override fun startSpeechInput(prompt: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE") // Locale.GERMANY.toLanguageTag() wäre robuster
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            // Fehler an ViewModel melden, damit es den UI-Zustand aktualisieren kann
            viewModel.setTrelloResult("Spracherkennung nicht verfügbar: ${e.message}") // Methode im ViewModel aufrufen
            // Ggf. auch eine Sprachausgabe direkt hier, falls ViewModel nicht erreichbar
             if (::textToSpeech.isInitialized) {
                 speakWithCallback("Spracherkennung nicht verfügbar.")
             }
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    // Folgende Member wurden ins ViewModel verschoben:
    // - private var currentGlobalState by mutableStateOf(...)
    // - private var spokenText by mutableStateOf(...)
    // - private var actionJsonForUi by mutableStateOf<JSONObject?>(...)
    // - private var trelloResult by mutableStateOf(...)
    // - private var currentFlowContextText by mutableStateOf(...)
    // - private lateinit var addCardFlow: AddCardFlow
    // - private lateinit var addListFlow: AddListFlow
    // - private lateinit var renameCardFlow: RenameCardFlow
    // - private val trelloKey: String by lazy { ... } (und andere API Keys)
    // - private var currentTrelloAction by mutableStateOf<String?>(...)
    // - override fun onCreate: Initialisierung der Flows und ihrer Dependencies
    // - private fun handleGeneralSpokenInput(input: String)
    // - private fun resetMultiTurnStateVariables()
    // - private fun resetAllStates()
    // - private fun handleSingleShotConfirmation(confirmation: String)
}