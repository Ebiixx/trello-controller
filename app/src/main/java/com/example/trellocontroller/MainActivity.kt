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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trellocontroller.flows.AddCardFlow
import com.example.trellocontroller.flows.AddCardFlowDependencies
import com.example.trellocontroller.flows.AddListFlow
import com.example.trellocontroller.flows.AddListFlowDependencies
import com.example.trellocontroller.flows.RenameCardFlow // Added
import com.example.trellocontroller.flows.RenameCardFlowDependencies // Added
import com.example.trellocontroller.utils.normalizeSpeechCommand // Import for normalizeSpeechCommand
import com.example.trellocontroller.utils.buildUiConfirmationText // Import for buildUiConfirmationText
import com.example.trellocontroller.ui.MainScreen
import com.example.trellocontroller.ui.theme.TrelloControllerTheme // Geändert von ui.TrelloControllerTheme
import org.json.JSONObject
import java.util.*


enum class ControllerState {
    WaitingForInitialCommand,

    // AddList Flow States werden durch AddListFlow.kt gehandhabt
    // AddCard Flow States werden durch AddCardFlow.kt gehandhabt
    // RenameCard Flow States werden durch RenameCardFlow.kt gehandhabt

    // General states
    WaitingForConfirmation, // Für Single-Shot Aktionen, die nicht in Flows sind
    ExecutingAction,        // Generischer Zustand, während ein Flow aktiv ist oder eine Single-Shot Aktion läuft
    Finished
}

class MainActivity : ComponentActivity() {

    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    private var currentGlobalState by mutableStateOf(ControllerState.WaitingForInitialCommand)
    private var spokenText by mutableStateOf("")
    private var actionJsonForUi by mutableStateOf<JSONObject?>(null) // Für Single-Shot Aktionen
    private var trelloResult by mutableStateOf("")
    private var currentFlowContextText by mutableStateOf("") // Für die UI-Anzeige des Flow-Kontexts

    // Flow Controller Instanzen
    private lateinit var addCardFlow: AddCardFlow
    private lateinit var addListFlow: AddListFlow
    private lateinit var renameCardFlow: RenameCardFlow // Added

    // Trello API Keys (aus BuildConfig)
    private val trelloKey: String by lazy { BuildConfig.TRELLO_API_KEY }
    private val trelloToken: String by lazy { BuildConfig.TRELLO_API_TOKEN }

    // Azure OpenAI API Keys (aus BuildConfig)
    private val azureApiKey: String by lazy { BuildConfig.AZURE_OPENAI_API_KEY }
    private val azureEndpoint: String by lazy { BuildConfig.AZURE_OPENAI_ENDPOINT }
    private val azureDeploymentName: String by lazy { BuildConfig.AZURE_OPENAI_DEPLOYMENT }
    private val azureApiVersion: String by lazy { BuildConfig.AZURE_OPENAI_API_VERSION }


    // Temporäre Zustandsvariablen für Multi-Turn-Interaktionen (hauptsächlich für AddList, bis refaktorisiert)
    private var currentTrelloAction by mutableStateOf<String?>(null)
    // These are now managed by individual flows or are not needed globally:
    // private var currentBoardId by mutableStateOf<String?>(null)
    // private var currentBoardName by mutableStateOf<String?>(null)
    // private var currentListId by mutableStateOf<String?>(null)
    // private var currentListName by mutableStateOf<String?>(null)

    // Von AI extrahierte, aber noch nicht bestätigte/verwendete Werte (hauptsächlich für AddList)
    // These are now managed by individual flows:
    // private var pendingBoardNameFromAI by mutableStateOf<String?>(null)
    // private var pendingListNameFromAI by mutableStateOf<String?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisiere AddCardFlow mit Abhängigkeiten (wie zuvor)
        val addCardFlowDependenciesImpl = object : AddCardFlowDependencies {
            override val trelloKey: String get() = this@MainActivity.trelloKey
            override val trelloToken: String get() = this@MainActivity.trelloToken

            override fun speakWithCallback(text: String, onDone: (() -> Unit)?) {
                this@MainActivity.speakWithCallback(text, onDone)
            }

            override fun startSpeechInput(prompt: String) {
                this@MainActivity.startSpeechInput(prompt)
            }

            override fun getBestMatchingBoardId(
                boardName: String,
                onResult: (boardId: String?, matchedBoardName: String?) -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.getBestMatchingBoardId(trelloKey, trelloToken, boardName, onResult, onError)
            }

            override fun getAllBoards(
                onResult: (List<Pair<String, String>>) -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.getAllBoards(trelloKey, trelloToken, onResult, onError)
            }

            override fun getBestMatchingListId(
                boardId: String,
                listName: String,
                onResult: (listId: String?, matchedListName: String?) -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.getBestMatchingListId(trelloKey, trelloToken, boardId, listName, onResult, onError)
            }

            override fun getAllLists(
                boardId: String,
                onResult: (List<Pair<String, String>>) -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.getAllLists(trelloKey, trelloToken, boardId, onResult, onError)
            }

            override fun addCardToTrello(
                listId: String,
                cardName: String,
                cardDesc: String,
                onSuccess: () -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.addCardToTrello(trelloKey, trelloToken, listId, cardName, cardDesc, onSuccess, onError)
            }

            override fun onFlowComplete(message: String, success: Boolean) {
                runOnUiThread {
                    trelloResult = message
                    currentFlowContextText = "" // Flow-Kontext leeren
                    speakWithCallback(message) {
                        currentGlobalState = ControllerState.WaitingForInitialCommand
                    }
                }
            }

            override fun updateUiContext(context: String) {
                runOnUiThread {
                    currentFlowContextText = context
                }
            }
        }
        addCardFlow = AddCardFlow(addCardFlowDependenciesImpl)

        // Initialisiere AddListFlow mit Abhängigkeiten
        val addListFlowDependenciesImpl = object : AddListFlowDependencies {
            override val trelloKey: String get() = this@MainActivity.trelloKey
            override val trelloToken: String get() = this@MainActivity.trelloToken

            override fun speakWithCallback(text: String, onDone: (() -> Unit)?) {
                this@MainActivity.speakWithCallback(text, onDone)
            }

            override fun startSpeechInput(prompt: String) {
                this@MainActivity.startSpeechInput(prompt)
            }

            override fun getBestMatchingBoardId(
                boardName: String,
                onResult: (boardId: String?, matchedBoardName: String?) -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.getBestMatchingBoardId(trelloKey, trelloToken, boardName, onResult, onError)
            }

            override fun getAllBoards(
                onResult: (List<Pair<String, String>>) -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.getAllBoards(trelloKey, trelloToken, onResult, onError)
            }

            override fun addListToTrello(
                boardId: String,
                listName: String,
                onSuccess: () -> Unit,
                onError: (String) -> Unit
            ) {
                com.example.trellocontroller.addListToBoard(trelloKey, trelloToken, boardId, listName, onSuccess, onError)
            }

            override fun onFlowComplete(message: String, success: Boolean) {
                runOnUiThread {
                    trelloResult = message
                    currentFlowContextText = "" // Flow-Kontext leeren
                    speakWithCallback(message) {
                        currentGlobalState = ControllerState.WaitingForInitialCommand
                    }
                }
            }

            override fun updateUiContext(context: String) {
                runOnUiThread {
                    currentFlowContextText = context
                }
            }
        }
        addListFlow = AddListFlow(addListFlowDependenciesImpl)

        // Initialisiere RenameCardFlow mit Abhängigkeiten
        val renameCardFlowDependenciesImpl = object : RenameCardFlowDependencies {
            override val trelloKey: String get() = this@MainActivity.trelloKey
            override val trelloToken: String get() = this@MainActivity.trelloToken

            override fun speakWithCallback(text: String, onDone: (() -> Unit)?) {
                this@MainActivity.speakWithCallback(text, onDone)
            }

            override fun startSpeechInput(prompt: String) {
                this@MainActivity.startSpeechInput(prompt)
            }

            override fun getBestMatchingBoardId(boardName: String, onResult: (boardId: String?, matchedBoardName: String?) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getBestMatchingBoardId(trelloKey, trelloToken, boardName, onResult, onError)
            }

            override fun getAllBoards(onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getAllBoards(trelloKey, trelloToken, onResult, onError)
            }

            override fun getBestMatchingListId(boardId: String, listName: String, onResult: (listId: String?, matchedListName: String?) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getBestMatchingListId(trelloKey, trelloToken, boardId, listName, onResult, onError)
            }

            override fun getAllLists(boardId: String, onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getAllLists(trelloKey, trelloToken, boardId, onResult, onError)
            }

            override fun getCardsFromList(listId: String, onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getCardsFromList(trelloKey, trelloToken, listId, onResult, onError)
            }

            override fun renameCardOnTrello(cardId: String, newName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.renameCardOnTrello(trelloKey, trelloToken, cardId, newName, onSuccess, onError)
            }

            override fun onFlowComplete(message: String, success: Boolean) {
                runOnUiThread {
                    trelloResult = message
                    currentFlowContextText = ""
                    speakWithCallback(message) {
                        currentGlobalState = ControllerState.WaitingForInitialCommand
                    }
                }
            }

            override fun updateUiContext(context: String) {
                runOnUiThread {
                    currentFlowContextText = context
                }
            }
        }
        renameCardFlow = RenameCardFlow(renameCardFlowDependenciesImpl)


        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.GERMAN
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        val callback = utteranceCallbacks.remove(utteranceId)
                        callback?.invoke()
                    }
                    override fun onError(utteranceId: String?) {
                        utteranceCallbacks.remove(utteranceId)
                    }
                })
            }
        }

        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0) ?: ""
                val fixedSpoken = normalizeSpeechCommand(spoken) // Normalisierung hier
                spokenText = fixedSpoken

                if (addCardFlow.isActive()) {
                    addCardFlow.handleSpokenInput(fixedSpoken) // Übergabe der normalisierten Eingabe
                } else if (::addListFlow.isInitialized && addListFlow.isActive()) { // Prüfe Initialisierung
                    addListFlow.handleSpokenInput(fixedSpoken)
                } else if (::renameCardFlow.isInitialized && renameCardFlow.isActive()) { // Added
                    renameCardFlow.handleSpokenInput(fixedSpoken)
                } else {
                    handleGeneralSpokenInput(fixedSpoken)
                }
            } else {
                // Spracheingabe fehlgeschlagen oder abgebrochen
                if (addCardFlow.isActive()) {
                    speakWithCallback("Spracheingabe fehlgeschlagen. Bitte erneut versuchen oder 'Abbrechen' sagen.") {
                        // Flow könnte hier seine letzte Frage wiederholen
                    }
                } else if (::addListFlow.isInitialized && addListFlow.isActive()) {
                     speakWithCallback("Spracheingabe fehlgeschlagen. Bitte erneut versuchen oder 'Abbrechen' sagen.") {
                        // Flow könnte hier seine letzte Frage wiederholen
                    }
                } else if (::renameCardFlow.isInitialized && renameCardFlow.isActive()) { // Added
                    speakWithCallback("Spracheingabe fehlgeschlagen. Bitte erneut versuchen oder 'Abbrechen' sagen.") {
                        // Flow könnte hier seine letzte Frage wiederholen
                    }
                } else {
                     speakWithCallback("Spracheingabe fehlgeschlagen.") {
                        if (currentGlobalState != ControllerState.WaitingForInitialCommand) {
                             currentGlobalState = ControllerState.WaitingForInitialCommand
                        }
                     }
                }
            }
        }

        setContent {
            TrelloControllerTheme { // Use TrelloControllerTheme from ui package
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val contextualInfo = if (currentFlowContextText.isNotBlank()) {
                        currentFlowContextText
                    } else if (actionJsonForUi != null && currentGlobalState == ControllerState.WaitingForConfirmation) {
                        buildUiConfirmationText(actionJsonForUi!!)
                    } else {
                        ""
                    }

                    MainScreen(
                        statusText = currentGlobalState.name,
                        spokenText = spokenText,
                        contextualInfoText = contextualInfo,
                        trelloResultText = trelloResult,
                        onStartSpeechOrResetClick = {
                            if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
                                textToSpeech.stop()
                            }
                            resetAllStates()
                            startSpeechInput("Was möchtest du tun?")
                        }
                    )
                }
            }
        }
    }

    private fun handleGeneralSpokenInput(input: String) {
         when (currentGlobalState) {
             ControllerState.WaitingForInitialCommand -> {
                 actionJsonForUi = null
                 resetMultiTurnStateVariables() // Globale Variablen zurücksetzen, die nicht von Flows verwaltet werden

                 val prompt = buildTrelloPrompt(input)

                 askAzureOpenAI(
                     prompt, azureApiKey, azureEndpoint, azureDeploymentName, azureApiVersion,
                     onResult = { aiResultString ->
                         runOnUiThread {
                             try {
                                 val obj = JSONObject(aiResultString)
                                 val action = obj.optString("action")
                                 currentTrelloAction = action // Kann für Single-Shot Aktionen nützlich sein

                                 if (action == "add_card") {
                                     currentGlobalState = ControllerState.ExecutingAction
                                     addCardFlow.start(obj)
                                 } else if (action == "add_list") {
                                     currentGlobalState = ControllerState.ExecutingAction
                                     addListFlow.start(obj) // Starte den AddListFlow
                                 } else if (action == "rename_card") { // Added
                                     currentGlobalState = ControllerState.ExecutingAction
                                     renameCardFlow.start(obj)
                                 } else {
                                     // Fallback für andere Aktionen (Single-Shot-Flow)
                                     actionJsonForUi = obj
                                     if (action.isNotBlank()) {
                                         val confirmationText = buildUiConfirmationText(obj) // Use imported function
                                         speakWithCallback(confirmationText) { startSpeechInput("Bitte mit Ja oder Nein antworten") }
                                         currentGlobalState = ControllerState.WaitingForConfirmation
                                     } else {
                                         speakWithCallback("Ich habe die Aktion nicht verstanden.") { startSpeechInput("Was möchtest du tun?") }
                                     }
                                 }
                             } catch (e: Exception) {
                                 trelloResult = "Fehler beim Verarbeiten der KI-Antwort: ${e.message}"
                                 speakWithCallback(trelloResult) { currentGlobalState = ControllerState.WaitingForInitialCommand }
                             }
                         }
                     },
                     onError = { errorMsg ->
                         runOnUiThread {
                             trelloResult = "Fehler bei der Anfrage an die KI: $errorMsg"
                             speakWithCallback(trelloResult) { currentGlobalState = ControllerState.WaitingForInitialCommand }
                         }
                     }
                 )
             }
             ControllerState.WaitingForConfirmation -> handleSingleShotConfirmation(input)
             ControllerState.ExecutingAction -> { /* Warten bis Flow fertig oder anderer Zustand gesetzt wird */ }
             ControllerState.Finished -> {
                 resetAllStates() // Stellt sicher, dass alles zurückgesetzt wird
             }
         }
    }

    private fun resetMultiTurnStateVariables() {
        // Nur noch Variablen zurücksetzen, die NICHT von den Flow-Klassen verwaltet werden.
        currentTrelloAction = null
    }

    private fun resetAllStates() {
        if (::addCardFlow.isInitialized && addCardFlow.isActive()) addCardFlow.resetState()
        if (::addListFlow.isInitialized && addListFlow.isActive()) addListFlow.resetState() // Neu
        if (::renameCardFlow.isInitialized && renameCardFlow.isActive()) renameCardFlow.resetState() // Added
        resetMultiTurnStateVariables()
        spokenText = ""
        trelloResult = ""
        actionJsonForUi = null
        currentFlowContextText = ""
        currentGlobalState = ControllerState.WaitingForInitialCommand
    }

    private fun speakWithCallback(text: String, onDone: (() -> Unit)? = null) {
        val utteranceId = UUID.randomUUID().toString()
        utteranceCallbacks[utteranceId] = onDone
        textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    private fun startSpeechInput(prompt: String = "Ich höre...") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            trelloResult = "Spracherkennung nicht verfügbar: ${e.message}"
            speakWithCallback(trelloResult)
        }
    }

    private val utteranceCallbacks = mutableMapOf<String, (() -> Unit)?>()

    private fun handleSingleShotConfirmation(confirmation: String) {
        if (confirmation.contains("ja")) {
            actionJsonForUi?.let { json ->
                val action = json.optString("action")
                trelloResult = "Aktion '$action' wird ausgeführt (Dummy für Single-Shot)..."
                speakWithCallback("Aktion wird ausgeführt.") {
                    resetAllStates()
                }
            } ?: run {
                speakWithCallback("Keine Aktion zum Bestätigen vorhanden.") { resetAllStates() }
            }
        } else {
            speakWithCallback("Okay, Aktion abgebrochen.") { resetAllStates() }
        }
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}