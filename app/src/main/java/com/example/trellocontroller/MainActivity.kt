package com.example.trellocontroller

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import org.json.JSONObject
import java.util.*

enum class ControllerState {
    WaitingForInitialCommand,

    // Add Card Flow
    AddCard_WaitingForBoardName,
    AddCard_ConfirmBoardNotFound,
    AddCard_WaitingForBoardNotFoundConfirmation,

    AddCard_WaitingForListName,
    AddCard_ConfirmListNotFound,
    AddCard_WaitingForListNotFoundConfirmation,

    AddCard_WaitingForCardTitle,
    AddCard_ConfirmCreation,

    // General states
    WaitingForConfirmation, // For full commands parsed by AI (old behavior)
    ExecutingAction,
    Finished
}

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    // State variables for multi-turn conversation
    private var currentTrelloAction by mutableStateOf<String?>(null)
    private var currentBoardId by mutableStateOf<String?>(null)
    private var currentBoardName by mutableStateOf<String?>(null)
    private var currentListId by mutableStateOf<String?>(null)
    private var currentListName by mutableStateOf<String?>(null)
    private var currentCardTitle by mutableStateOf<String?>(null)
    // private var currentCardDesc by mutableStateOf<String?>(null) // Future extension

    private fun startSpeechInput(prompt: String = "Bitte antworte jetzt") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        speechLauncher.launch(intent)
    }

    private fun speakWithCallback(text: String, onDone: (() -> Unit)? = null) {
        val utteranceId = System.currentTimeMillis().toString()
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
            override fun onDone(utteranceId_: String?) {
                if (utteranceId_ == utteranceId) {
                    runOnUiThread {
                        textToSpeech?.setOnUtteranceProgressListener(null)
                        onDone?.invoke()
                    }
                }
            }
        }
        if (onDone != null) {
            textToSpeech?.setOnUtteranceProgressListener(listener)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            textToSpeech?.setOnUtteranceProgressListener(null)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun levenshtein(lhs: String, rhs: String): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        val dp = Array(lhsLen + 1) { IntArray(rhsLen + 1) }
        for (i in 0..lhsLen) dp[i][0] = i
        for (j in 0..rhsLen) dp[0][j] = j
        for (i in 1..lhsLen) {
            for (j in 1..rhsLen) {
                val cost = if (lhs[i - 1].equals(rhs[j - 1], true)) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[lhsLen][rhsLen]
    }

    private fun findBestMatch(
        name: String,
        candidates: List<String>
    ): String? {
        if (name.isBlank()) {
            return null
        }
        val lowerName = name.lowercase()
        val dynamicMaxDist = (lowerName.length / 3).coerceAtLeast(2).coerceAtMost(6)

        return candidates
            .minByOrNull { candidate ->
                val candLower = candidate.lowercase()
                if (candLower.contains(lowerName) || lowerName.contains(candLower)) {
                    0
                } else {
                    levenshtein(candLower, lowerName)
                }
            }
            ?.takeIf { bestMatchCandidate ->
                val candLower = bestMatchCandidate.lowercase()
                val actualDistance = if (candLower.contains(lowerName) || lowerName.contains(candLower)) {
                    0
                } else {
                    levenshtein(candLower, lowerName)
                }
                actualDistance <= dynamicMaxDist
            }
    }

    private fun getBestMatchingBoardId(
        key: String, token: String, boardName: String,
        onResult: (String?, String?) -> Unit, onError: (String) -> Unit
    ) {
        getAllBoards(key, token, { boards ->
            val names = boards.map { it.first }
            val best = findBestMatch(boardName, names)
            if (best != null) {
                val id = boards.first { it.first == best }.second
                onResult(id, best)
            } else onResult(null, null)
        }, onError)
    }

    private fun getBestMatchingListId(
        key: String, token: String, boardId: String, listName: String,
        onResult: (String?, String?) -> Unit, onError: (String) -> Unit
    ) {
        getAllLists(key, token, boardId, { lists ->
            val names = lists.map { it.first }
            val best = findBestMatch(listName, names)
            if (best != null) {
                val id = lists.first { it.first == best }.second
                onResult(id, best)
            } else onResult(null, null)
        }, onError)
    }

    private fun buildConfirmationText(actionObj: JSONObject): String {
        val action = actionObj.optString("action", "")
        val board = actionObj.optString("board", "")
        val list = actionObj.optString("list", "")
        val title = actionObj.optString("title", "")
        val desc = actionObj.optString("desc", "")
        // ... (other fields if needed for other actions)
        return when (action) {
            "add_card" -> "Ich werde eine neue Karte mit dem Titel '$title'${if (desc.isNotBlank()) " und Beschreibung '$desc'" else ""} in der Liste '$list' im Board '$board' erstellen. Möchtest du das tun?"
            "add_list" -> "Ich werde eine neue Liste mit dem Namen '$list' im Board '$board' anlegen. Ist das korrekt?"
            // ... (other actions)
            else -> "Aktion erkannt: ${actionObj.optString("action")} für Board '$board', Liste '$list', Titel '$title'. Möchtest du das ausführen?"
        }
    }

    fun normalizeSpeechCommand(input: String): String {
        return input
            .replace(Regex("\\bimport\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bim board\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bauf dem board\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bauf board\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bin board\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bin der liste\\b", RegexOption.IGNORE_CASE), "in der Liste")
            .replace(Regex("\\bauf der liste\\b", RegexOption.IGNORE_CASE), "in der Liste")
            .replace(Regex("\\bin liste\\b", RegexOption.IGNORE_CASE), "in Liste")
            .replace(Regex("\\bex\\b", RegexOption.IGNORE_CASE), "X")
            .replace(Regex("\\bin die\\b", RegexOption.IGNORE_CASE), "in die")
            .replace(Regex("\\bkarte\\b", RegexOption.IGNORE_CASE), "Karte")
            .replace(Regex("\\bneue karte\\b", RegexOption.IGNORE_CASE), "neue Karte")
            .replace(Regex("\\berstelle karte\\b", RegexOption.IGNORE_CASE), "erstelle Karte")
            .replace(Regex("\\berstell mir ne karte\\b", RegexOption.IGNORE_CASE), "erstelle Karte")
            .replace(Regex("\\bmach ne karte\\b", RegexOption.IGNORE_CASE), "erstelle Karte")
            .replace(Regex("\\bliste\\b", RegexOption.IGNORE_CASE), "Liste")
            .replace(Regex("\\bneue liste\\b", RegexOption.IGNORE_CASE), "neue Liste")
            .replace(Regex("\\berstelle liste\\b", RegexOption.IGNORE_CASE), "erstelle Liste")
            .replace(Regex("\\bboard\\b", RegexOption.IGNORE_CASE), "Board")
            .replace(Regex("\\bto do\\b", RegexOption.IGNORE_CASE), "To Do")
            .replace(Regex("\\btodo\\b", RegexOption.IGNORE_CASE), "To Do")
            .replace(Regex("\\bto-do\\b", RegexOption.IGNORE_CASE), "To Do")
            .replace(Regex("\\b tu du\\b", RegexOption.IGNORE_CASE), " To Do")
            .replace(Regex("\\bbacklog\\b", RegexOption.IGNORE_CASE), "Backlog")
            .replace(Regex("\\bbeck blog\\b", RegexOption.IGNORE_CASE), "Backlog")
            .replace(Regex("\\b beck lock\\b", RegexOption.IGNORE_CASE), " Backlog")
            .replace(Regex("\\bdone\\b", RegexOption.IGNORE_CASE), "Done")
            .replace(Regex("\\bdann\\b", RegexOption.IGNORE_CASE), "Done")
            .replace(Regex("\\bfertig\\b", RegexOption.IGNORE_CASE), "Done")
            .replace(Regex("\\bcard\\b", RegexOption.IGNORE_CASE), "Karte")
            .replace(Regex("\\btrott\\b", RegexOption.IGNORE_CASE), "Trello")
            .replace(Regex("\\btrailer\\b", RegexOption.IGNORE_CASE), "Trello")
            .replace(Regex("\\bdrello\\b", RegexOption.IGNORE_CASE), "Trello")
            .replace(Regex("\\bimboard\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bkarte in\\b", RegexOption.IGNORE_CASE), "Karte in")
            .replace(Regex("\\bkarte von\\b", RegexOption.IGNORE_CASE), "Karte von")
            .replace(Regex("\\bfüge hinzu\\b", RegexOption.IGNORE_CASE), "füge hinzu")
            .replace(Regex("\\bhinzufügen\\b", RegexOption.IGNORE_CASE), "hinzufügen")
            .replace(Regex("\\bverschiebe\\b", RegexOption.IGNORE_CASE), "verschiebe")
            .replace(Regex("\\bkommentar\\b", RegexOption.IGNORE_CASE), "Kommentar")
            .replace(Regex("\\bkommentiere\\b", RegexOption.IGNORE_CASE), "kommentiere")
            .replace(Regex("\\bjob agent\\b", RegexOption.IGNORE_CASE), "Jobagent")
            .replace(Regex("\\bshop agent\\b", RegexOption.IGNORE_CASE), "Jobagent")
            .replace("ae", "ä").replace("oe", "ö").replace("ue", "ü")
            .replace("Ae", "Ä").replace("Oe", "Ö").replace("Ue", "Ü")
    }

    private fun resetCardCreationFlow(nextState: ControllerState = ControllerState.WaitingForInitialCommand) {
        currentTrelloAction = null
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentListName = null
        currentCardTitle = null
        // currentCardDesc = null
        // Update the main state variable if needed, passed as parameter
        // this.state = nextState // 'state' is a var in onCreate's scope
    }

    private fun proceedToAskBoardName() {
        speakWithCallback("Okay, ich werde eine Karte erstellen. In welchem Board soll die Karte erstellt werden?") {
            startSpeechInput("Bitte nenne das Board")
        }
        // state = ControllerState.AddCard_WaitingForBoardName // This will be set by the caller
    }

    private fun proceedToAskListName() {
        speakWithCallback("Okay, im Board '$currentBoardName'. In welcher Liste soll die Karte erstellt werden?") {
            startSpeechInput("Bitte nenne die Liste")
        }
        // state = ControllerState.AddCard_WaitingForListName // This will be set by the caller
    }

    private fun proceedToAskCardTitle() {
        speakWithCallback("Okay, in Liste '$currentListName' im Board '$currentBoardName'. Wie soll die Karte heißen?") {
            startSpeechInput("Bitte nenne den Titel der Karte")
        }
        // state = ControllerState.AddCard_WaitingForCardTitle // This will be set by the caller
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val azureApiKey = BuildConfig.AZURE_OPENAI_API_KEY
        val azureEndpoint = BuildConfig.AZURE_OPENAI_ENDPOINT
        val azureDeployment = BuildConfig.AZURE_OPENAI_DEPLOYMENT
        val azureApiVersion = BuildConfig.AZURE_OPENAI_API_VERSION
        val trelloKey = BuildConfig.TRELLO_API_KEY
        val trelloToken = BuildConfig.TRELLO_API_TOKEN

        var state by mutableStateOf(ControllerState.WaitingForInitialCommand)
        var spokenText by mutableStateOf("")
        var actionJsonForUi by mutableStateOf<JSONObject?>(null) // For UI display of initial AI parse
        var trelloResult by mutableStateOf("")

        textToSpeech = TextToSpeech(this) {
            if (it != TextToSpeech.ERROR) {
                textToSpeech?.language = Locale.getDefault()
            }
        }

        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
                val spoken = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.getOrNull(0) ?: ""
                val fixedSpoken = normalizeSpeechCommand(spoken)
                spokenText = fixedSpoken // Update UI with what was heard

                when (state) {
                    ControllerState.WaitingForInitialCommand -> {
                        val prompt = buildTrelloPrompt(fixedSpoken)
                        askAzureOpenAI(
                            prompt, azureApiKey, azureEndpoint, azureDeployment, azureApiVersion,
                            onResult = { aiResultString ->
                                runOnUiThread {
                                    try {
                                        val obj = JSONObject(aiResultString)
                                        actionJsonForUi = obj // Update UI with AI interpretation
                                        val action = obj.optString("action")

                                        if (action == "add_card") {
                                            currentTrelloAction = "add_card"
                                            val boardFromAI = obj.optString("board").takeIf { it.isNotBlank() }
                                            val listFromAI = obj.optString("list").takeIf { it.isNotBlank() }
                                            val titleFromAI = obj.optString("title").takeIf { it.isNotBlank() }

                                            if (boardFromAI == null) {
                                                proceedToAskBoardName()
                                                state = ControllerState.AddCard_WaitingForBoardName
                                            } else {
                                                getBestMatchingBoardId(trelloKey, trelloToken, boardFromAI,
                                                    onResult = { boardId, matchedBoard ->
                                                        if (boardId != null) {
                                                            currentBoardId = boardId
                                                            currentBoardName = matchedBoard
                                                            if (listFromAI == null) {
                                                                proceedToAskListName()
                                                                state = ControllerState.AddCard_WaitingForListName
                                                            } else {
                                                                getBestMatchingListId(trelloKey, trelloToken, boardId, listFromAI,
                                                                    onResult = { listId, matchedList ->
                                                                        if (listId != null) {
                                                                            currentListId = listId
                                                                            currentListName = matchedList
                                                                            if (titleFromAI == null) {
                                                                                proceedToAskCardTitle()
                                                                                state = ControllerState.AddCard_WaitingForCardTitle
                                                                            } else {
                                                                                currentCardTitle = titleFromAI
                                                                                val confirmMsg = "Ich werde eine neue Karte mit dem Titel '${currentCardTitle}' in der Liste '${currentListName}' im Board '${currentBoardName}' erstellen. Möchtest du das tun?"
                                                                                speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                                                                                state = ControllerState.AddCard_ConfirmCreation
                                                                            }
                                                                        } else { // List from AI not found
                                                                            speakWithCallback("Die von der KI erkannte Liste '$listFromAI' wurde nicht gefunden.") {
                                                                                proceedToAskListName()
                                                                            }
                                                                            state = ControllerState.AddCard_WaitingForListName
                                                                        }
                                                                    },
                                                                    onError = {speakWithCallback("Fehler bei Listensuche für '${listFromAI}'.") { proceedToAskListName()}; state = ControllerState.AddCard_WaitingForListName }
                                                                )
                                                            }
                                                        } else { // Board from AI not found
                                                            speakWithCallback("Das von der KI erkannte Board '$boardFromAI' wurde nicht gefunden.") {
                                                                proceedToAskBoardName()
                                                            }
                                                            state = ControllerState.AddCard_WaitingForBoardName
                                                        }
                                                    },
                                                    onError = { speakWithCallback("Fehler bei Boardsuche für '${boardFromAI}'.") { proceedToAskBoardName() }; state = ControllerState.AddCard_WaitingForBoardName }
                                                )
                                            }
                                        } else if (action.isNotBlank()) { // Other actions, use old flow
                                            val confirmationText = buildConfirmationText(obj)
                                            speakWithCallback(confirmationText) {
                                                startSpeechInput("Bitte mit Ja oder Nein antworten")
                                            }
                                            state = ControllerState.WaitingForConfirmation
                                        } else {
                                            speakWithCallback("Ich habe die Aktion nicht verstanden. Bitte versuche es erneut.") { startSpeechInput("Was möchtest du tun?") }
                                        }
                                    } catch (e: Exception) {
                                        speakWithCallback("Fehler beim Verarbeiten der KI-Antwort.") { startSpeechInput("Was möchtest du tun?") }
                                    }
                                }
                            },
                            onError = { errorMsg ->
                                runOnUiThread {
                                    speakWithCallback("Fehler bei der Anfrage an die KI: $errorMsg") { startSpeechInput("Was möchtest du tun?") }
                                }
                            }
                        )
                    }

                    ControllerState.AddCard_WaitingForBoardName -> {
                        val boardNameInput = fixedSpoken
                        getBestMatchingBoardId(trelloKey, trelloToken, boardNameInput,
                            onResult = { boardId, matchedBoardName ->
                                if (boardId != null) {
                                    currentBoardId = boardId
                                    currentBoardName = matchedBoardName
                                    proceedToAskListName()
                                    state = ControllerState.AddCard_WaitingForListName
                                } else {
                                    speakWithCallback("Das Board '$boardNameInput' wurde nicht gefunden. Soll ich dir mögliche Boards nennen?") {
                                        startSpeechInput("Ja oder Nein?")
                                    }
                                    state = ControllerState.AddCard_ConfirmBoardNotFound
                                }
                            },
                            onError = {speakWithCallback("Fehler bei Boardsuche. Bitte erneut versuchen.") { proceedToAskBoardName() }; state = ControllerState.AddCard_WaitingForBoardName }
                        )
                    }
                    ControllerState.AddCard_ConfirmBoardNotFound -> {
                        if (fixedSpoken.lowercase().contains("ja")) {
                            getAllBoards(trelloKey, trelloToken, { boards ->
                                val boardNames = boards.map { it.first }
                                if (boardNames.isNotEmpty()) {
                                    val boardListText = "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. In welchem Board?"
                                    speakWithCallback(boardListText) { startSpeechInput("Bitte nenne das Board") }
                                } else {
                                    speakWithCallback("Ich konnte keine Boards finden.")
                                }
                                state = ControllerState.AddCard_WaitingForBoardName
                            }, onError = {speakWithCallback("Fehler beim Laden der Boards.") { proceedToAskBoardName() }; state = ControllerState.AddCard_WaitingForBoardName })
                        } else {
                            speakWithCallback("Okay, Vorgang abgebrochen.") { resetCardCreationFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }

                    ControllerState.AddCard_WaitingForListName -> {
                        val listNameInput = fixedSpoken
                        getBestMatchingListId(trelloKey, trelloToken, currentBoardId!!, listNameInput,
                            onResult = { listId, matchedListName ->
                                if (listId != null) {
                                    currentListId = listId
                                    currentListName = matchedListName
                                    proceedToAskCardTitle()
                                    state = ControllerState.AddCard_WaitingForCardTitle
                                } else {
                                    speakWithCallback("Die Liste '$listNameInput' wurde im Board '$currentBoardName' nicht gefunden. Soll ich dir mögliche Listen nennen?") {
                                        startSpeechInput("Ja oder Nein?")
                                    }
                                    state = ControllerState.AddCard_ConfirmListNotFound
                                }
                            },
                            onError = {speakWithCallback("Fehler bei Listensuche. Bitte erneut versuchen.") { proceedToAskListName() }; state = ControllerState.AddCard_WaitingForListName }
                        )
                    }
                    ControllerState.AddCard_ConfirmListNotFound -> {
                        if (fixedSpoken.lowercase().contains("ja")) {
                            getAllLists(trelloKey, trelloToken, currentBoardId!!, { lists ->
                                val listNames = lists.map { it.first }
                                if (listNames.isNotEmpty()) {
                                    val listText = "Mögliche Listen sind: ${listNames.joinToString(", ", limit = 5)}. In welcher Liste?"
                                    speakWithCallback(listText) { startSpeechInput("Bitte nenne die Liste") }
                                } else {
                                    speakWithCallback("Keine Listen im Board '$currentBoardName' gefunden.")
                                }
                                state = ControllerState.AddCard_WaitingForListName
                            }, onError = {speakWithCallback("Fehler beim Laden der Listen.") { proceedToAskListName() }; state = ControllerState.AddCard_WaitingForListName })
                        } else {
                            speakWithCallback("Okay, Vorgang abgebrochen.") { resetCardCreationFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }

                    ControllerState.AddCard_WaitingForCardTitle -> {
                        currentCardTitle = fixedSpoken
                        val confirmMsg = "Ich werde eine neue Karte mit dem Titel '${currentCardTitle}' in der Liste '${currentListName}' im Board '${currentBoardName}' erstellen. Möchtest du das tun?"
                        speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                        state = ControllerState.AddCard_ConfirmCreation
                    }

                    ControllerState.AddCard_ConfirmCreation -> {
                        if (fixedSpoken.lowercase().contains("ja")) {
                            if (currentTrelloAction == "add_card" && currentListId != null && currentCardTitle != null) {
                                // state = ControllerState.ExecutingAction // UI will show this via trelloResult
                                addCardToTrello(trelloKey, trelloToken, currentListId!!, currentCardTitle!!, "", // desc is empty for now
                                    onSuccess = {
                                        runOnUiThread {
                                            trelloResult = "Karte '${currentCardTitle}' in Liste '${currentListName}' im Board '${currentBoardName}' erstellt."
                                            speakWithCallback(trelloResult)
                                            resetCardCreationFlow()
                                            state = ControllerState.Finished
                                        }
                                    },
                                    onError = { errMsg ->
                                        runOnUiThread {
                                            trelloResult = "Fehler: $errMsg"
                                            speakWithCallback(trelloResult)
                                            resetCardCreationFlow()
                                            state = ControllerState.WaitingForInitialCommand
                                        }
                                    }
                                )
                            } else { /* Should not happen if logic is correct */ speakWithCallback("Interner Fehler."); resetCardCreationFlow(); state = ControllerState.WaitingForInitialCommand }
                        } else {
                            speakWithCallback("Okay, Karte nicht erstellt.") { resetCardCreationFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }

                    ControllerState.WaitingForConfirmation -> { // Handles other actions like add_list if AI provided full info
                        if (fixedSpoken.lowercase().contains("ja")) {
                            val obj = actionJsonForUi // Use the stored JSON from initial AI parse
                            if (obj != null) {
                                val action = obj.optString("action")
                                // This is where you'd put the logic from the *original* WaitingForConfirmation state
                                // For example, for "add_list":
                                if (action == "add_list") {
                                    val boardNameToMatch = obj.optString("board")
                                    val listNameToAdd = obj.optString("list")
                                    if (boardNameToMatch.isNotBlank() && listNameToAdd.isNotBlank()) {
                                        getBestMatchingBoardId(trelloKey, trelloToken, boardNameToMatch,
                                            onResult = { boardId, matchedBoard ->
                                                if (boardId != null) {
                                                    addListToBoard(trelloKey, trelloToken, boardId, listNameToAdd,
                                                        onSuccess = { runOnUiThread { trelloResult = "Liste '$listNameToAdd' in Board '$matchedBoard' erstellt."; speakWithCallback(trelloResult); state = ControllerState.Finished } },
                                                        onError = { errMsg -> runOnUiThread { trelloResult = "Fehler: $errMsg"; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand } }
                                                    )
                                                } else { runOnUiThread { trelloResult = "Board '$boardNameToMatch' nicht gefunden."; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand } }
                                            },
                                            onError = { errMsg -> runOnUiThread { trelloResult = "Fehler Boardsuche: $errMsg"; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand } }
                                        )
                                    } else { runOnUiThread { trelloResult = "Board/Listenname fehlt."; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand } }
                                } else {
                                    runOnUiThread { trelloResult = "Aktion '$action' noch nicht vollständig im Dialog implementiert."; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand }
                                }
                            } else { runOnUiThread { trelloResult = "Keine Aktion zum Bestätigen."; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand } }
                        } else {
                            speakWithCallback("Okay, Aktion abgebrochen.") { state = ControllerState.WaitingForInitialCommand }
                        }
                        actionJsonForUi = null // Clear after use
                    }

                    ControllerState.Finished -> {
                        spokenText = ""
                        trelloResult = "" // Will be set by the successful action
                        actionJsonForUi = null
                        resetCardCreationFlow() // Ensure multi-turn states are cleared
                        state = ControllerState.WaitingForInitialCommand
                        // Optional: speak "Bereit für nächsten Befehl" after a short delay or directly allow new input.
                    }
                    else -> {
                        // Should not happen if all states are handled
                        resetCardCreationFlow()
                        state = ControllerState.WaitingForInitialCommand
                    }
                }
            }
        }
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = {
                                        // If in a flow, the prompt for startSpeechInput is set by the flow logic.
                                        // If idle, use a generic prompt.
                                        val prompt = when (state) {
                                            ControllerState.WaitingForInitialCommand -> "Was möchtest du tun?"
                                            // Specific prompts are handled by speakWithCallback's onDone
                                            else -> "Ich höre..." // Generic fallback, should be overridden by flow
                                        }
                                        if (state == ControllerState.WaitingForInitialCommand || state == ControllerState.Finished) {
                                            // Reset UI fields for a fresh command
                                            spokenText = ""
                                            trelloResult = ""
                                            actionJsonForUi = null
                                            resetCardCreationFlow()
                                            state = ControllerState.WaitingForInitialCommand
                                            startSpeechInput(prompt)
                                        } else {
                                            // If in the middle of a flow, the speech input is usually triggered by speakWithCallback's onDone.
                                            // This button click might interrupt or restart. For now, let it restart if clicked mid-flow.
                                            // Or, better, let the current flow dictate the prompt if it's waiting for input.
                                            // For simplicity, a general click here might just restart the initial command prompt.
                                            resetCardCreationFlow()
                                            state = ControllerState.WaitingForInitialCommand
                                            startSpeechInput("Was möchtest du tun?")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
                                ) {
                                    Icon(Icons.Default.Mic, "Sprich jetzt", Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Sprich jetzt", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
                                Text("Status: $state", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(10.dp))
                                Text("🗣️ Erkannt:", style = MaterialTheme.typography.labelMedium)
                                Text(spokenText, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))

                                // Display current multi-turn context or initial AI parse
                                val currentActionInfo = StringBuilder()
                                if (currentTrelloAction != null) {
                                    currentActionInfo.append("Aktion: $currentTrelloAction")
                                    if (currentBoardName != null) currentActionInfo.append("\nBoard: $currentBoardName")
                                    if (currentListName != null) currentActionInfo.append("\nListe: $currentListName")
                                    if (currentCardTitle != null) currentActionInfo.append("\nTitel: $currentCardTitle")
                                } else if (actionJsonForUi != null) {
                                    currentActionInfo.append(buildConfirmationText(actionJsonForUi!!))
                                }

                                if (currentActionInfo.isNotBlank()) {
                                    Text("💬 Kontext/KI:", style = MaterialTheme.typography.labelMedium)
                                    Text(currentActionInfo.toString(), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(8.dp))
                                }

                                Text("📝 Trello-Status:", style = MaterialTheme.typography.labelMedium)
                                Text(trelloResult, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}