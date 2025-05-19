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
    AddCard_ConfirmBoardNotFound, // Used by AddList too
    // AddCard_WaitingForBoardNotFoundConfirmation, // Combined into ConfirmBoardNotFound

    AddCard_WaitingForListName,
    AddCard_ConfirmListNotFound,
    // AddCard_WaitingForListNotFoundConfirmation, // Combined into ConfirmListNotFound

    AddCard_WaitingForCardTitle,
    AddCard_ConfirmCreation,

    // Add List Flow
    AddList_WaitingForBoardName,
    // AddList_ConfirmBoardNotFound, // Reuses AddCard_ConfirmBoardNotFound
    // AddList_WaitingForBoardNotFoundConfirmation, // Reuses AddCard_...

    AddList_WaitingForNewListName,
    AddList_ConfirmCreation,


    // General states
    WaitingForConfirmation, // For full commands parsed by AI (old behavior for other actions)
    ExecutingAction, // Might not be explicitly used if UI updates on trelloResult
    Finished
}

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

    // State variables for multi-turn conversation
    private var currentTrelloAction by mutableStateOf<String?>(null)
    private var currentBoardId by mutableStateOf<String?>(null)
    private var currentBoardName by mutableStateOf<String?>(null)
    private var currentListId by mutableStateOf<String?>(null) // Used for card's list
    private var currentListName by mutableStateOf<String?>(null) // Used for card's list name
    private var currentCardTitle by mutableStateOf<String?>(null)
    private var currentNewListName by mutableStateOf<String?>(null) // For add_list action

    private var pendingBoardNameFromAI by mutableStateOf<String?>(null)
    private var pendingListNameFromAI by mutableStateOf<String?>(null)
    private var pendingCardTitleFromAI by mutableStateOf<String?>(null)
    private var pendingNewListNameFromAI by mutableStateOf<String?>(null)


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

    // This is for the old "single-shot" AI confirmation
    private fun buildUiConfirmationText(actionObj: JSONObject): String {
        val action = actionObj.optString("action", "")
        val board = actionObj.optString("board", "")
        val list = actionObj.optString("list", "") // For add_card, this is target list. For add_list, this is new list name.
        val title = actionObj.optString("title", "")
        // ... other fields ...
        return when (action) {
            "add_card" -> "KI: Karte '$title' in Liste '$list' auf Board '$board' erstellen?"
            "add_list" -> "KI: Liste '$list' auf Board '$board' erstellen?"
            // ... other actions for single-shot confirmation
            else -> "KI: Aktion ${actionObj.optString("action")} mit Details: ${actionObj}. Bestätigen?"
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

    private fun resetMultiTurnFlow() {
        currentTrelloAction = null
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentListName = null
        currentCardTitle = null
        currentNewListName = null

        pendingBoardNameFromAI = null
        pendingListNameFromAI = null
        pendingCardTitleFromAI = null
        pendingNewListNameFromAI = null
    }

    private fun proceedToAskBoardName(actionType: String) { // actionType "Karte" or "Liste"
        speakWithCallback("Okay, ich werde eine $actionType erstellen. In welchem Board soll die $actionType erstellt werden?") {
            startSpeechInput("Bitte nenne das Board")
        }
    }

    private fun proceedToAskListNameForCard() { // For add_card
        speakWithCallback("Okay, im Board '$currentBoardName'. In welcher Liste soll die Karte erstellt werden?") {
            startSpeechInput("Bitte nenne die Liste")
        }
    }

    private fun proceedToAskNewListName() { // For add_list
        speakWithCallback("Okay, im Board '$currentBoardName'. Wie soll die neue Liste heißen?") {
            startSpeechInput("Bitte nenne den Namen der neuen Liste")
        }
    }

    private fun proceedToAskCardTitle() {
        speakWithCallback("Okay, in Liste '$currentListName' im Board '$currentBoardName'. Wie soll die Karte heißen?") {
            startSpeechInput("Bitte nenne den Titel der Karte")
        }
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
        var actionJsonForUi by mutableStateOf<JSONObject?>(null)
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
                spokenText = fixedSpoken

                when (state) {
                    ControllerState.WaitingForInitialCommand -> {
                        actionJsonForUi = null // Clear previous AI full parse for UI
                        resetMultiTurnFlow()   // Clear multi-turn states
                        val prompt = buildTrelloPrompt(fixedSpoken)
                        askAzureOpenAI(
                            prompt, azureApiKey, azureEndpoint, azureDeployment, azureApiVersion,
                            onResult = { aiResultString ->
                                runOnUiThread {
                                    try {
                                        val obj = JSONObject(aiResultString)
                                        actionJsonForUi = obj // Store for UI, if it's a single-shot action
                                        val action = obj.optString("action")
                                        pendingBoardNameFromAI = obj.optString("board").takeIf { it.isNotBlank() }
                                        pendingListNameFromAI = obj.optString("list").takeIf { it.isNotBlank() } // For add_card: target list; For add_list: new list name

                                        when (action) {
                                            "add_card" -> {
                                                currentTrelloAction = "add_card"
                                                pendingCardTitleFromAI = obj.optString("title").takeIf { it.isNotBlank() }
                                                if (pendingBoardNameFromAI == null) {
                                                    proceedToAskBoardName("Karte")
                                                    state = ControllerState.AddCard_WaitingForBoardName
                                                } else {
                                                    // Board name provided by AI, attempt to use it
                                                    getBestMatchingBoardId(trelloKey, trelloToken, pendingBoardNameFromAI!!,
                                                        onResult = { boardId, matchedBoard ->
                                                            if (boardId != null) {
                                                                currentBoardId = boardId
                                                                currentBoardName = matchedBoard
                                                                if (pendingListNameFromAI == null) {
                                                                    proceedToAskListNameForCard()
                                                                    state = ControllerState.AddCard_WaitingForListName
                                                                } else {
                                                                    getBestMatchingListId(trelloKey, trelloToken, boardId, pendingListNameFromAI!!,
                                                                        onResult = { listId, matchedList ->
                                                                            if (listId != null) {
                                                                                currentListId = listId
                                                                                currentListName = matchedList
                                                                                if (pendingCardTitleFromAI == null) {
                                                                                    proceedToAskCardTitle()
                                                                                    state = ControllerState.AddCard_WaitingForCardTitle
                                                                                } else {
                                                                                    currentCardTitle = pendingCardTitleFromAI
                                                                                    val confirmMsg = "Ich werde eine neue Karte mit dem Titel '${currentCardTitle}' in der Liste '${currentListName}' im Board '${currentBoardName}' erstellen. Möchtest du das tun?"
                                                                                    speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                                                                                    state = ControllerState.AddCard_ConfirmCreation
                                                                                }
                                                                            } else { // List from AI not found
                                                                                speakWithCallback("Die von der KI erkannte Liste '$pendingListNameFromAI' wurde nicht gefunden.") { proceedToAskListNameForCard() }
                                                                                state = ControllerState.AddCard_WaitingForListName
                                                                            }
                                                                        },
                                                                        onError = { speakWithCallback("Fehler bei Listensuche für '$pendingListNameFromAI'.") { proceedToAskListNameForCard() }; state = ControllerState.AddCard_WaitingForListName }
                                                                    )
                                                                }
                                                            } else { // Board from AI not found
                                                                speakWithCallback("Das von der KI erkannte Board '$pendingBoardNameFromAI' wurde nicht gefunden.") { proceedToAskBoardName("Karte") }
                                                                state = ControllerState.AddCard_WaitingForBoardName
                                                            }
                                                        },
                                                        onError = { speakWithCallback("Fehler bei Boardsuche für '$pendingBoardNameFromAI'.") { proceedToAskBoardName("Karte") }; state = ControllerState.AddCard_WaitingForBoardName }
                                                    )
                                                }
                                            }
                                            "add_list" -> {
                                                currentTrelloAction = "add_list"
                                                pendingNewListNameFromAI = pendingListNameFromAI // AI's "list" field is the new list name here
                                                if (pendingBoardNameFromAI == null) {
                                                    proceedToAskBoardName("Liste")
                                                    state = ControllerState.AddList_WaitingForBoardName
                                                } else {
                                                    // Board name provided by AI
                                                    getBestMatchingBoardId(trelloKey, trelloToken, pendingBoardNameFromAI!!,
                                                        onResult = { boardId, matchedBoard ->
                                                            if (boardId != null) {
                                                                currentBoardId = boardId
                                                                currentBoardName = matchedBoard
                                                                if (pendingNewListNameFromAI == null) {
                                                                    proceedToAskNewListName()
                                                                    state = ControllerState.AddList_WaitingForNewListName
                                                                } else {
                                                                    currentNewListName = pendingNewListNameFromAI
                                                                    val confirmMsg = "Ich werde eine neue Liste mit dem Namen '${currentNewListName}' im Board '${currentBoardName}' erstellen. Ist das korrekt?"
                                                                    speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                                                                    state = ControllerState.AddList_ConfirmCreation
                                                                }
                                                            } else { // Board from AI not found
                                                                speakWithCallback("Das von der KI erkannte Board '$pendingBoardNameFromAI' wurde nicht gefunden.") { proceedToAskBoardName("Liste") }
                                                                state = ControllerState.AddList_WaitingForBoardName
                                                            }
                                                        },
                                                        onError = { speakWithCallback("Fehler bei Boardsuche für '$pendingBoardNameFromAI'.") { proceedToAskBoardName("Liste") }; state = ControllerState.AddList_WaitingForBoardName }
                                                    )
                                                }
                                            }
                                            // ... other actions that might have a multi-turn flow ...
                                            else -> { // Fallback to old single-shot confirmation for other actions
                                                if (action.isNotBlank()) {
                                                    val confirmationText = buildUiConfirmationText(obj) // Use specific UI builder
                                                    speakWithCallback(confirmationText) { startSpeechInput("Bitte mit Ja oder Nein antworten") }
                                                    state = ControllerState.WaitingForConfirmation
                                                } else {
                                                    speakWithCallback("Ich habe die Aktion nicht verstanden. Bitte versuche es erneut.") { startSpeechInput("Was möchtest du tun?") }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        speakWithCallback("Fehler beim Verarbeiten der KI-Antwort: ${e.message}") { startSpeechInput("Was möchtest du tun?") }
                                        state = ControllerState.WaitingForInitialCommand
                                    }
                                }
                            },
                            onError = { errorMsg ->
                                runOnUiThread {
                                    speakWithCallback("Fehler bei der Anfrage an die KI: $errorMsg") { startSpeechInput("Was möchtest du tun?") }
                                    state = ControllerState.WaitingForInitialCommand
                                }
                            }
                        )
                    }

                    ControllerState.AddCard_WaitingForBoardName, ControllerState.AddList_WaitingForBoardName -> {
                        val boardNameInput = fixedSpoken
                        getBestMatchingBoardId(trelloKey, trelloToken, boardNameInput,
                            onResult = { boardId, matchedBoardName ->
                                if (boardId != null) {
                                    currentBoardId = boardId
                                    currentBoardName = matchedBoardName
                                    if (currentTrelloAction == "add_card") {
                                        proceedToAskListNameForCard()
                                        state = ControllerState.AddCard_WaitingForListName
                                    } else if (currentTrelloAction == "add_list") {
                                        proceedToAskNewListName()
                                        state = ControllerState.AddList_WaitingForNewListName
                                    }
                                } else {
                                    speakWithCallback("Das Board '$boardNameInput' wurde nicht gefunden. Soll ich dir mögliche Boards nennen?") {
                                        startSpeechInput("Ja oder Nein?")
                                    }
                                    state = ControllerState.AddCard_ConfirmBoardNotFound // Generic "confirm board not found"
                                }
                            },
                            onError = {
                                speakWithCallback("Fehler bei Boardsuche. Bitte erneut versuchen.") {
                                    if (currentTrelloAction == "add_card") proceedToAskBoardName("Karte") else proceedToAskBoardName("Liste")
                                }
                                state = if (currentTrelloAction == "add_card") ControllerState.AddCard_WaitingForBoardName else ControllerState.AddList_WaitingForBoardName
                            }
                        )
                    }
                    ControllerState.AddCard_ConfirmBoardNotFound -> { // Handles both add_card and add_list board not found
                        if (fixedSpoken.lowercase().contains("ja")) {
                            getAllBoards(trelloKey, trelloToken, { boards ->
                                val boardNames = boards.map { it.first }
                                if (boardNames.isNotEmpty()) {
                                    val boardListText = "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. In welchem Board?"
                                    speakWithCallback(boardListText) { startSpeechInput("Bitte nenne das Board") }
                                } else {
                                    speakWithCallback("Ich konnte keine Boards finden.")
                                }
                                // Return to the correct "waiting for board name" state based on current action
                                state = if (currentTrelloAction == "add_card") ControllerState.AddCard_WaitingForBoardName else ControllerState.AddList_WaitingForBoardName
                            }, onError = {
                                speakWithCallback("Fehler beim Laden der Boards.") {
                                     if (currentTrelloAction == "add_card") proceedToAskBoardName("Karte") else proceedToAskBoardName("Liste")
                                }
                                state = if (currentTrelloAction == "add_card") ControllerState.AddCard_WaitingForBoardName else ControllerState.AddList_WaitingForBoardName
                            })
                        } else {
                            speakWithCallback("Okay, Vorgang abgebrochen.") { resetMultiTurnFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }

                    ControllerState.AddCard_WaitingForListName -> { // Specific to add_card
                        val listNameInput = fixedSpoken
                        getBestMatchingListId(trelloKey, trelloToken, currentBoardId!!, listNameInput,
                            onResult = { listId, matchedListName ->
                                if (listId != null) {
                                    currentListId = listId
                                    currentListName = matchedListName
                                    if (pendingCardTitleFromAI != null) { // If AI gave title initially
                                        currentCardTitle = pendingCardTitleFromAI
                                        val confirmMsg = "Ich werde eine neue Karte mit dem Titel '${currentCardTitle}' in der Liste '${currentListName}' im Board '${currentBoardName}' erstellen. Möchtest du das tun?"
                                        speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                                        state = ControllerState.AddCard_ConfirmCreation
                                    } else {
                                        proceedToAskCardTitle()
                                        state = ControllerState.AddCard_WaitingForCardTitle
                                    }
                                } else {
                                    speakWithCallback("Die Liste '$listNameInput' wurde im Board '$currentBoardName' nicht gefunden. Soll ich dir mögliche Listen nennen?") {
                                        startSpeechInput("Ja oder Nein?")
                                    }
                                    state = ControllerState.AddCard_ConfirmListNotFound
                                }
                            },
                            onError = {speakWithCallback("Fehler bei Listensuche. Bitte erneut versuchen.") { proceedToAskListNameForCard() }; state = ControllerState.AddCard_WaitingForListName }
                        )
                    }
                    ControllerState.AddCard_ConfirmListNotFound -> { // Specific to add_card
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
                            }, onError = {speakWithCallback("Fehler beim Laden der Listen.") { proceedToAskListNameForCard() }; state = ControllerState.AddCard_WaitingForListName })
                        } else {
                            speakWithCallback("Okay, Vorgang abgebrochen.") { resetMultiTurnFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }

                    ControllerState.AddCard_WaitingForCardTitle -> { // Specific to add_card
                        currentCardTitle = fixedSpoken
                        val confirmMsg = "Ich werde eine neue Karte mit dem Titel '${currentCardTitle}' in der Liste '${currentListName}' im Board '${currentBoardName}' erstellen. Möchtest du das tun?"
                        speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                        state = ControllerState.AddCard_ConfirmCreation
                    }

                    ControllerState.AddCard_ConfirmCreation -> { // Specific to add_card
                        if (fixedSpoken.lowercase().contains("ja")) {
                            if (currentTrelloAction == "add_card" && currentListId != null && currentCardTitle != null) {
                                addCardToTrello(trelloKey, trelloToken, currentListId!!, currentCardTitle!!, "",
                                    onSuccess = {
                                        runOnUiThread {
                                            trelloResult = "Karte '${currentCardTitle}' in Liste '${currentListName}' im Board '${currentBoardName}' erstellt."
                                            speakWithCallback(trelloResult) { state = ControllerState.Finished }
                                        }
                                    },
                                    onError = { errMsg -> runOnUiThread { trelloResult = "Fehler: $errMsg"; speakWithCallback(trelloResult) { state = ControllerState.WaitingForInitialCommand } } }
                                )
                            } else { speakWithCallback("Interner Fehler bei Karten-Erstellung."); resetMultiTurnFlow(); state = ControllerState.WaitingForInitialCommand }
                        } else {
                            speakWithCallback("Okay, Karte nicht erstellt.") { resetMultiTurnFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }

                    // Add List Flow States
                    ControllerState.AddList_WaitingForNewListName -> {
                        currentNewListName = fixedSpoken
                        val confirmMsg = "Ich werde eine neue Liste mit dem Namen '${currentNewListName}' im Board '${currentBoardName}' erstellen. Ist das korrekt?"
                        speakWithCallback(confirmMsg) { startSpeechInput("Ja oder Nein?") }
                        state = ControllerState.AddList_ConfirmCreation
                    }

                    ControllerState.AddList_ConfirmCreation -> {
                        if (fixedSpoken.lowercase().contains("ja")) {
                            if (currentTrelloAction == "add_list" && currentBoardId != null && currentNewListName != null) {
                                addListToBoard(trelloKey, trelloToken, currentBoardId!!, currentNewListName!!,
                                    onSuccess = {
                                        runOnUiThread {
                                            trelloResult = "Liste '${currentNewListName}' im Board '${currentBoardName}' erstellt."
                                            speakWithCallback(trelloResult) { state = ControllerState.Finished }
                                        }
                                    },
                                    onError = { errMsg -> runOnUiThread { trelloResult = "Fehler: $errMsg"; speakWithCallback(trelloResult) { state = ControllerState.WaitingForInitialCommand } } }
                                )
                            } else { speakWithCallback("Interner Fehler bei Listen-Erstellung."); resetMultiTurnFlow(); state = ControllerState.WaitingForInitialCommand }
                        } else {
                            speakWithCallback("Okay, Liste nicht erstellt.") { resetMultiTurnFlow(); state = ControllerState.WaitingForInitialCommand }
                        }
                    }


                    ControllerState.WaitingForConfirmation -> { // Handles other actions if AI provided full info (old flow)
                        if (fixedSpoken.lowercase().contains("ja")) {
                            val obj = actionJsonForUi
                            if (obj != null) {
                                val action = obj.optString("action")
                                // This section is for actions NOT handled by multi-turn dialogs above
                                // Example: if you had "delete_card" fully parsed by AI
                                if (action != "add_card" && action != "add_list") {
                                    // TODO: Implement execution for other single-shot confirmed actions
                                    runOnUiThread { trelloResult = "Aktion '$action' (Einzelschritt) wird ausgeführt..."; speakWithCallback(trelloResult); state = ControllerState.Finished }
                                } else {
                                     runOnUiThread { trelloResult = "Aktion '$action' sollte im Dialog behandelt werden."; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand }
                                }
                            } else { runOnUiThread { trelloResult = "Keine Aktion zum Bestätigen."; speakWithCallback(trelloResult); state = ControllerState.WaitingForInitialCommand } }
                        } else {
                            speakWithCallback("Okay, Aktion abgebrochen.") { state = ControllerState.WaitingForInitialCommand }
                        }
                        actionJsonForUi = null
                    }

                    ControllerState.Finished -> {
                        // SpokenText and TrelloResult are part of the UI and will be cleared on next initial command
                        // actionJsonForUi is cleared
                        // resetMultiTurnFlow() is called when transitioning TO WaitingForInitialCommand or from Finished to new command
                        // The main loop will transition to WaitingForInitialCommand or user can click button
                        // For now, let button click handle full reset to WaitingForInitialCommand
                        // Or, after speakWithCallback in success handlers, set state = ControllerState.WaitingForInitialCommand
                        // Let's ensure reset happens before next command
                        resetMultiTurnFlow()
                        // spokenText = "" // Already handled by UI logic or next command
                        // trelloResult = "" // Already handled by UI logic or next command
                        state = ControllerState.WaitingForInitialCommand // Ready for next command
                        // Optionally, speak "Bereit für nächsten Befehl"
                    }
                    else -> {
                        resetMultiTurnFlow()
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
                        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                                        resetMultiTurnFlow()
                                        spokenText = ""
                                        trelloResult = ""
                                        actionJsonForUi = null
                                        state = ControllerState.WaitingForInitialCommand
                                        startSpeechInput("Was möchtest du tun?")
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

                                val currentActionInfo = StringBuilder()
                                if (currentTrelloAction == "add_card") {
                                    currentActionInfo.append("Aktion: Karte erstellen")
                                    if (currentBoardName != null) currentActionInfo.append("\nBoard: $currentBoardName")
                                    if (currentListName != null) currentActionInfo.append("\nZielliste: $currentListName")
                                    if (currentCardTitle != null) currentActionInfo.append("\nKartentitel: $currentCardTitle")
                                } else if (currentTrelloAction == "add_list") {
                                    currentActionInfo.append("Aktion: Liste erstellen")
                                    if (currentBoardName != null) currentActionInfo.append("\nBoard: $currentBoardName")
                                    if (currentNewListName != null) currentActionInfo.append("\nNeuer Listenname: $currentNewListName")
                                } else if (actionJsonForUi != null) { // Fallback for single-shot AI results
                                    currentActionInfo.append(buildUiConfirmationText(actionJsonForUi!!))
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