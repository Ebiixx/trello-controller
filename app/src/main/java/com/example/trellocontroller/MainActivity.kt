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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trellocontroller.flows.AddCardFlow
import com.example.trellocontroller.flows.AddCardFlowDependencies
// Importiere Typography explizit, falls es in einer separaten Datei liegt, ansonsten ist es hier definiert
// import com.example.trellocontroller.ui.theme.Typography // Beispielhafter Pfad
import org.json.JSONObject
import java.util.*


enum class ControllerState {
    WaitingForInitialCommand,

    // AddList Flow States (bleiben vorerst, bis AddListFlow implementiert ist)
    AddList_WaitingForBoardName,
    AddList_ConfirmBoardNotFound,
    AddList_WaitingForNewListName,
    AddList_ConfirmCreation,

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
    // private lateinit var addListFlow: AddListFlow // Beispiel für zukünftige Flows

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
    private var currentBoardId by mutableStateOf<String?>(null)
    private var currentBoardName by mutableStateOf<String?>(null)
    private var currentListId by mutableStateOf<String?>(null)
    private var currentListName by mutableStateOf<String?>(null)
    private var currentNewListName by mutableStateOf<String?>(null) // Spezifisch für AddList

    // Von AI extrahierte, aber noch nicht bestätigte/verwendete Werte (hauptsächlich für AddList)
    private var pendingBoardNameFromAI by mutableStateOf<String?>(null)
    private var pendingListNameFromAI by mutableStateOf<String?>(null)
    private var pendingNewListNameFromAI by mutableStateOf<String?>(null)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                boardName: String, // Dies ist die normalisierte Eingabe vom AddCardFlow
                onResult: (boardId: String?, matchedBoardName: String?) -> Unit,
                onError: (String) -> Unit
            ) {
                this@MainActivity.getBestMatchingBoardId(trelloKey, trelloToken, boardName, onResult, onError)
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
                this@MainActivity.getBestMatchingListId(trelloKey, trelloToken, boardId, listName, onResult, onError)
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
                } else {
                    handleGeneralSpokenInput(fixedSpoken)
                }
            } else {
                if (addCardFlow.isActive()) {
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
            TrelloControllerTheme { // Verwende das hier definierte Theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    private fun handleGeneralSpokenInput(input: String) {
        when (currentGlobalState) {
            ControllerState.WaitingForInitialCommand -> {
                actionJsonForUi = null
                resetMultiTurnStateVariables()

                val prompt = buildTrelloPrompt(input) // userMessage ist jetzt 'input'

                askAzureOpenAI(
                    prompt, azureApiKey, azureEndpoint, azureDeploymentName, azureApiVersion, // Korrigiert
                    onResult = { aiResultString ->
                        runOnUiThread {
                            try {
                                val obj = JSONObject(aiResultString)
                                val action = obj.optString("action")
                                currentTrelloAction = action

                                if (action == "add_card") {
                                    currentGlobalState = ControllerState.ExecutingAction
                                    addCardFlow.start(obj)
                                } else if (action == "add_list") {
                                    pendingBoardNameFromAI = obj.optString("board").takeIf { it.isNotBlank() }
                                    pendingNewListNameFromAI = obj.optString("list").takeIf { it.isNotBlank() }

                                    if (pendingBoardNameFromAI != null) {
                                        proceedToAskBoardName(pendingBoardNameFromAI!!, forListCreation = true)
                                    } else {
                                        speakWithCallback("In welchem Board soll die neue Liste erstellt werden?") {
                                            startSpeechInput("Bitte nenne das Board für die neue Liste")
                                        }
                                        currentGlobalState = ControllerState.AddList_WaitingForBoardName
                                    }
                                } else {
                                    actionJsonForUi = obj
                                    if (action.isNotBlank()) {
                                        val confirmationText = buildUiConfirmationText(obj)
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
            ControllerState.AddList_WaitingForBoardName -> handleAddListBoardName(input)
            ControllerState.AddList_ConfirmBoardNotFound -> handleAddListConfirmBoardNotFound(input)
            ControllerState.AddList_WaitingForNewListName -> handleAddListNewListName(input)
            ControllerState.AddList_ConfirmCreation -> handleAddListConfirmCreation(input)
            ControllerState.WaitingForConfirmation -> handleSingleShotConfirmation(input)
            ControllerState.ExecutingAction -> { /* Warten bis Flow fertig oder anderer Zustand gesetzt wird */ }
            ControllerState.Finished -> {
                resetAllStates()
            }
        }
    }

    private val utteranceCallbacks = mutableMapOf<String, (() -> Unit)?>()
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

    private fun normalizeSpeechCommand(command: String): String {
        return command.lowercase(Locale.GERMAN).trim()
    }

    private fun resetMultiTurnStateVariables() {
        currentTrelloAction = null
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentListName = null
        currentNewListName = null
        pendingBoardNameFromAI = null
        pendingListNameFromAI = null
        pendingNewListNameFromAI = null
    }

    private fun resetAllStates() {
        if (::addCardFlow.isInitialized && addCardFlow.isActive()) addCardFlow.resetState()
        resetMultiTurnStateVariables()
        spokenText = ""
        trelloResult = ""
        actionJsonForUi = null
        currentFlowContextText = ""
        currentGlobalState = ControllerState.WaitingForInitialCommand
    }

    private fun getBestMatchingBoardId(
        apiKey: String,
        apiToken: String,
        boardNameInput: String, // Hier wird mit der normalisierten Eingabe gesucht
        onResult: (boardId: String?, matchedBoardName: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        com.example.trellocontroller.getAllBoards(apiKey, apiToken,
            onResult = { boards ->
                val exactMatch = boards.firstOrNull { it.first.equals(boardNameInput, ignoreCase = true) }
                if (exactMatch != null) {
                    onResult(exactMatch.second, exactMatch.first)
                    return@getAllBoards
                }
                val partialMatch = boards.firstOrNull { it.first.contains(boardNameInput, ignoreCase = true) }
                if (partialMatch != null) {
                    onResult(partialMatch.second, partialMatch.first)
                } else {
                    onResult(null, null)
                }
            },
            onError = onError
        )
    }

    private fun getBestMatchingListId(
        apiKey: String,
        apiToken: String,
        boardId: String,
        listNameInput: String,
        onResult: (listId: String?, matchedListName: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        com.example.trellocontroller.getAllLists(apiKey, apiToken, boardId,
            onResult = { lists ->
                val exactMatch = lists.firstOrNull { it.first.equals(listNameInput, ignoreCase = true) }
                if (exactMatch != null) {
                    onResult(exactMatch.second, exactMatch.first)
                    return@getAllLists
                }
                val partialMatch = lists.firstOrNull { it.first.contains(listNameInput, ignoreCase = true) }
                if (partialMatch != null) {
                    onResult(partialMatch.second, partialMatch.first)
                } else {
                    onResult(null, null)
                }
            },
            onError = onError
        )
    }

    private fun buildUiConfirmationText(json: JSONObject): String {
        val action = json.optString("action", "Unbekannte Aktion")
        val board = json.optString("board", "")
        val list = json.optString("list", "")
        val title = json.optString("title", "")
        return when (action) {
            "add_card" -> "Soll Karte '$title' zu Liste '$list' auf Board '$board' hinzugefügt werden?" // Wird jetzt vom Flow gehandhabt
            "add_list" -> "Soll Liste '$list' zu Board '$board' hinzugefügt werden?"
            else -> "Aktion: $action, Details: ${json.toString(2)}"
        }
    }

    private fun proceedToAskBoardName(boardName: String, forListCreation: Boolean = false) {
        getBestMatchingBoardId(trelloKey, trelloToken, boardName,
            onResult = { boardId, matchedBoardName ->
                if (boardId != null) {
                    currentBoardId = boardId
                    currentBoardName = matchedBoardName
                    if (forListCreation) {
                        if (pendingNewListNameFromAI != null) {
                            currentNewListName = pendingNewListNameFromAI
                            pendingNewListNameFromAI = null
                            speakWithCallback("Okay, im Board '$currentBoardName'. Die neue Liste soll '$currentNewListName' heißen. Ist das richtig?") {
                                startSpeechInput("Ja oder Nein?")
                            }
                            currentGlobalState = ControllerState.AddList_ConfirmCreation
                        } else {
                            speakWithCallback("Okay, im Board '$currentBoardName'. Wie soll die neue Liste heißen?") {
                                startSpeechInput("Bitte nenne den Namen der neuen Liste")
                            }
                            currentGlobalState = ControllerState.AddList_WaitingForNewListName
                        }
                    }
                } else {
                    speakWithCallback("Das Board '$boardName' wurde nicht gefunden. Soll ich dir mögliche Boards nennen?") {
                        startSpeechInput("Ja oder Nein?")
                    }
                    currentGlobalState = if (forListCreation) ControllerState.AddList_ConfirmBoardNotFound else ControllerState.WaitingForInitialCommand
                }
            },
            onError = { error ->
                speakWithCallback("Fehler bei der Boardsuche: $error. Bitte erneut versuchen.") {
                    startSpeechInput("Bitte nenne das Board")
                }
                currentGlobalState = if (forListCreation) ControllerState.AddList_WaitingForBoardName else ControllerState.WaitingForInitialCommand
            }
        )
    }

    private fun handleAddListBoardName(spokenBoardName: String) {
        proceedToAskBoardName(spokenBoardName, forListCreation = true)
    }

    private fun handleAddListConfirmBoardNotFound(confirmation: String) {
        if (confirmation.contains("ja")) {
            com.example.trellocontroller.getAllBoards(trelloKey, trelloToken,
                onResult = { boards ->
                    val boardNames = boards.map { it.first }
                    val text = if (boardNames.isNotEmpty()) {
                        "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. In welchem Board soll die Liste erstellt werden?"
                    } else {
                        "Ich konnte keine Boards finden. Bitte nenne ein Board."
                    }
                    speakWithCallback(text) { startSpeechInput("Bitte nenne das Board") }
                    currentGlobalState = ControllerState.AddList_WaitingForBoardName
                },
                onError = {
                    speakWithCallback("Fehler beim Laden der Boards. Bitte nenne ein Board.") {
                        startSpeechInput("Bitte nenne das Board")
                    }
                    currentGlobalState = ControllerState.AddList_WaitingForBoardName
                }
            )
        } else {
            speakWithCallback("Okay, Vorgang abgebrochen.") { resetAllStates() }
        }
    }

    private fun handleAddListNewListName(spokenListName: String) {
        currentNewListName = spokenListName
        speakWithCallback("Okay, die neue Liste im Board '$currentBoardName' soll '$currentNewListName' heißen. Ist das richtig?") {
            startSpeechInput("Ja oder Nein?")
        }
        currentGlobalState = ControllerState.AddList_ConfirmCreation
    }

    private fun handleAddListConfirmCreation(confirmation: String) {
        if (confirmation.contains("ja")) {
            if (currentBoardId != null && currentNewListName != null) {
                addListToBoard(trelloKey, trelloToken, currentBoardId!!, currentNewListName!!,
                    onSuccess = {
                        val successMsg = "Liste '$currentNewListName' wurde im Board '$currentBoardName' erstellt."
                        trelloResult = successMsg
                        speakWithCallback(successMsg) { resetAllStates() }
                    },
                    onError = { error ->
                        val errorMsg = "Fehler beim Erstellen der Liste: $error"
                        trelloResult = errorMsg
                        speakWithCallback(errorMsg) { resetAllStates() }
                    }
                )
            } else {
                speakWithCallback("Interner Fehler: Board ID oder Listenname fehlt.") { resetAllStates() }
            }
        } else {
            speakWithCallback("Okay, Liste nicht erstellt.") { resetAllStates() }
        }
    }

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

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Trello Sprachsteuerung",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = {
                        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking) {
                            textToSpeech.stop()
                        }
                        resetAllStates() // Ruft auch addCardFlow.resetState() auf
                        startSpeechInput("Was möchtest du tun?")
                    },
                    modifier = Modifier.fillMaxWidth().height(70.dp)
                ) {
                    Text("Sprachbefehl starten / Reset", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
                        Text("Status: ${currentGlobalState.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("🗣️ Erkannt:", style = MaterialTheme.typography.labelMedium)
                        Text(spokenText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))

                        if (currentFlowContextText.isNotBlank()) {
                            Text("💬 Kontext/Flow:", style = MaterialTheme.typography.labelMedium)
                            Text(currentFlowContextText, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        } else if (actionJsonForUi != null && currentGlobalState == ControllerState.WaitingForConfirmation) {
                            Text("💬 Kontext/KI:", style = MaterialTheme.typography.labelMedium)
                            Text(buildUiConfirmationText(actionJsonForUi!!), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }

                        Text("📝 Trello-Status:", style = MaterialTheme.typography.labelMedium)
                        Text(trelloResult, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("© 2024 TrelloController", style = MaterialTheme.typography.bodySmall) // Angepasst
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

// Theme (kann in eine eigene Datei ausgelagert werden, z.B. ui/theme/Theme.kt)
@Composable
fun TrelloControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme( // oder darkColorScheme
            primary = md_theme_light_primary,
            onPrimary = md_theme_light_onPrimary,
            background = md_theme_light_background,
            surface = md_theme_light_surface,
            // Füge hier weitere Farben deines Themes hinzu, falls benötigt
            // secondary = ...,
            // error = ...,
            // etc.
        ),
        typography = Typography, // Deine Typografie-Definition (siehe unten)
        content = content
    )
}

// Beispiel-Farben (ersetze diese mit deinen tatsächlichen Theme-Farben)
// Diese sollten idealerweise in einer eigenen Datei liegen (z.B. ui/theme/Color.kt)
val md_theme_light_primary = Color(0xFF00629B) // Trello Blau als Beispiel
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_background = Color(0xFFFDFCFB)
val md_theme_light_surface = Color(0xFFFDFCFB)

// Beispiel-Typografie (ersetze oder erweitere diese)
// Diese sollte idealerweise in einer eigenen Datei liegen (z.B. ui/theme/Type.kt)
val Typography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
)