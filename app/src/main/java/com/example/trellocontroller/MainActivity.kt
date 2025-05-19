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
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic

enum class ControllerState {
    WaitingForCommand,
    WaitingForConfirmation,
    ExecutingAction,
    Finished
}

class MainActivity : ComponentActivity() {
    private var textToSpeech: TextToSpeech? = null
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>

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

    // Levenshtein-Distanz & Fuzzy-Match für tolerantere Erkennung
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
        candidates: List<String>,
        maxDist: Int = 6
    ): String? {
        val lowerName = name.lowercase()
        return candidates
            .minByOrNull {
                val candLower = it.lowercase()
                if (candLower.contains(lowerName) || lowerName.contains(candLower)) 0 else levenshtein(candLower, lowerName)
            }
            ?.takeIf {
                val candLower = it.lowercase()
                candLower.contains(lowerName) ||
                        lowerName.contains(candLower) ||
                        levenshtein(candLower, lowerName) <= maxDist
            }
    }

    // Fuzzy Board/Listen-Finder:
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
        val comment = actionObj.optString("comment", "")
        val label = actionObj.optString("label", "")
        val member = actionObj.optString("member", "")
        val due = actionObj.optString("due", "")
        val targetList = actionObj.optString("target_list", "")
        return when (action) {
            "add_card" -> "Ich werde eine neue Karte mit dem Titel '$title'${if (desc.isNotBlank()) " und Beschreibung '$desc'" else ""} in der Liste '$list' im Board '$board' erstellen. Möchtest du das tun?"
            "add_list" -> "Ich werde eine neue Liste mit dem Namen '$list' im Board '$board' anlegen. Ist das korrekt?"
            "delete_card" -> "Ich werde die Karte '$title' aus der Liste '$list' im Board '$board' löschen. Bist du sicher?"
            "move_card" -> "Ich werde die Karte '$title' von der Liste '$list' in die Liste '$targetList' im Board '$board' verschieben. Ist das richtig?"
            "archive_card" -> "Ich werde die Karte '$title' in der Liste '$list' im Board '$board' archivieren. Bestätigen?"
            "add_comment" -> "Ich werde den Kommentar '$comment' zur Karte '$title' in der Liste '$list' im Board '$board' hinzufügen. Richtig so?"
            "add_due_date" -> "Ich werde das Fälligkeitsdatum '$due' für die Karte '$title' in der Liste '$list' im Board '$board' setzen. Okay?"
            "remove_due_date" -> "Ich werde das Fälligkeitsdatum für die Karte '$title' in der Liste '$list' im Board '$board' entfernen. Fortfahren?"
            "add_label" -> "Ich werde das Label '$label' zur Karte '$title' in der Liste '$list' im Board '$board' hinzufügen. Richtig?"
            "remove_label" -> "Ich werde das Label '$label' von der Karte '$title' in der Liste '$list' im Board '$board' entfernen. Fortfahren?"
            "assign_member" -> "Ich werde das Mitglied '$member' der Karte '$title' in der Liste '$list' im Board '$board' zuweisen. Richtig?"
            "remove_member" -> "Ich werde das Mitglied '$member' von der Karte '$title' in der Liste '$list' im Board '$board' entfernen. Bestätigen?"
            "rename_card" -> "Ich werde die Karte '$title' in der Liste '$list' im Board '$board' umbenennen. Richtig?"
            "update_desc" -> "Ich werde die Beschreibung der Karte '$title' in der Liste '$list' im Board '$board' aktualisieren. Fortfahren?"
            else -> "Aktion erkannt: ${actionObj.optString("action")} in Board '$board'. Möchtest du das ausführen?"
        }
    }

    fun normalizeSpeechCommand(input: String): String {
        return input
            .replace(Regex("\\bimport\\b", RegexOption.IGNORE_CASE), "im Board")
            .replace(Regex("\\bex\\b", RegexOption.IGNORE_CASE), "X")
            .replace(Regex("\\bin die\\b", RegexOption.IGNORE_CASE), "in die")
            .replace(Regex("\\bkarte\\b", RegexOption.IGNORE_CASE), "Karte")
            .replace(Regex("\\bliste\\b", RegexOption.IGNORE_CASE), "Liste")
            .replace(Regex("\\bboard\\b", RegexOption.IGNORE_CASE), "Board")
            // häufige Missverständnisse:
            .replace(Regex("\\bto do\\b", RegexOption.IGNORE_CASE), "To Do")
            .replace(Regex("\\btodo\\b", RegexOption.IGNORE_CASE), "To Do")
            .replace(Regex("\\bbacklog\\b", RegexOption.IGNORE_CASE), "Backlog")
            .replace(Regex("\\bdone\\b", RegexOption.IGNORE_CASE), "Done")
            .replace(Regex("\\bdann\\b", RegexOption.IGNORE_CASE), "Done")
            .replace(Regex("\\bcard\\b", RegexOption.IGNORE_CASE), "Karte")
            .replace(Regex("\\btrott\\b", RegexOption.IGNORE_CASE), "Trello") // falls „Trello“ als „Trott“ erkannt wird
            .replace(Regex("\\btrailer\\b", RegexOption.IGNORE_CASE), "Trello")
            .replace(Regex("\\bimboard\\b", RegexOption.IGNORE_CASE), "im Board")
            // Weitere Varianten:
            .replace(Regex("\\bkarte in\\b", RegexOption.IGNORE_CASE), "Karte in")
            .replace(Regex("\\bkarte von\\b", RegexOption.IGNORE_CASE), "Karte von")
            // Eigenname-Vervollständigungen (bei Listen- oder Board-Namen):
            .replace(Regex("\\bjob agent\\b", RegexOption.IGNORE_CASE), "Jobagent")
        // und was du sonst noch brauchst!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val azureApiKey = BuildConfig.AZURE_OPENAI_API_KEY
        val azureEndpoint = BuildConfig.AZURE_OPENAI_ENDPOINT
        val azureDeployment = BuildConfig.AZURE_OPENAI_DEPLOYMENT
        val azureApiVersion = BuildConfig.AZURE_OPENAI_API_VERSION
        val trelloKey = BuildConfig.TRELLO_API_KEY
        val trelloToken = BuildConfig.TRELLO_API_TOKEN

        var state by mutableStateOf(ControllerState.WaitingForCommand)
        var spokenText by mutableStateOf("")
        var aiResult by mutableStateOf("")
        var actionJson by mutableStateOf<JSONObject?>(null)
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
                    ControllerState.WaitingForCommand -> {
                        val prompt = buildTrelloPrompt(fixedSpoken)
                        askAzureOpenAI(
                            prompt,
                            azureApiKey,
                            azureEndpoint,
                            azureDeployment,
                            azureApiVersion,
                            onResult = { result ->
                                runOnUiThread {
                                    try {
                                        val obj = JSONObject(result)
                                        actionJson = obj
                                        val confirmationText = buildConfirmationText(obj)
                                        speakWithCallback(confirmationText) {
                                            startSpeechInput("Bitte mit Ja oder Nein antworten")
                                        }
                                        state = ControllerState.WaitingForConfirmation
                                    } catch (e: Exception) {
                                        aiResult = "Fehler beim Verarbeiten der Antwort."
                                        state = ControllerState.WaitingForCommand
                                    }
                                }
                            },
                            onError = {
                                runOnUiThread {
                                    aiResult = "Fehler: $it"
                                    Toast.makeText(this@MainActivity, aiResult, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                    ControllerState.WaitingForConfirmation -> {
                        if (spoken.lowercase().contains("ja")) {
                            val obj = actionJson
                            if (obj == null) {
                                Toast.makeText(this@MainActivity, "Keine Aktion erkannt.", Toast.LENGTH_SHORT).show()
                            } else {
                                val action = obj.optString("action")

                                // Fuzzy-Board-Finder (Glättung!)
                                getBestMatchingBoardId(trelloKey, trelloToken, obj.optString("board"),
                                    onResult = { boardId, usedBoard ->
                                        if (boardId == null) {
                                            runOnUiThread {
                                                trelloResult = "Board '${obj.optString("board")}' nicht gefunden."
                                                speakWithCallback(trelloResult)
                                                state = ControllerState.WaitingForCommand
                                            }
                                        } else {
                                            // Fuzzy-Listen-Finder (auch für add_card etc.!)
                                            val listName = obj.optString("list")
                                            if (action in listOf(
                                                    "add_card", "delete_card", "move_card", "archive_card", "add_comment",
                                                    "add_due_date", "remove_due_date", "add_label", "remove_label", "assign_member",
                                                    "remove_member", "rename_card", "update_desc"
                                                )) {
                                                getBestMatchingListId(trelloKey, trelloToken, boardId, listName,
                                                    onResult = { listId, usedList ->
                                                        if (listId == null) {
                                                            runOnUiThread {
                                                                trelloResult = "Liste '$listName' nicht gefunden."
                                                                speakWithCallback(trelloResult)
                                                                state = ControllerState.WaitingForCommand
                                                            }
                                                        } else {
                                                            // Beispiel: add_card
                                                            if (action == "add_card") {
                                                                addCardToTrello(
                                                                    trelloKey, trelloToken, listId,
                                                                    obj.optString("title"), obj.optString("desc"),
                                                                    onSuccess = {
                                                                        runOnUiThread {
                                                                            trelloResult = "Die Karte '${obj.optString("title")}' wurde erstellt."
                                                                            speakWithCallback(trelloResult)
                                                                            state = ControllerState.Finished
                                                                        }
                                                                    },
                                                                    onError = { errMsg ->
                                                                        runOnUiThread {
                                                                            trelloResult = "Fehler beim Erstellen: $errMsg"
                                                                            speakWithCallback(trelloResult)
                                                                            state = ControllerState.WaitingForCommand
                                                                        }
                                                                    }
                                                                )
                                                            } else {
                                                                // TODO: Die weiteren Features analog ergänzen!
                                                                runOnUiThread {
                                                                    trelloResult = "Diese Aktion ist noch nicht fertig implementiert."
                                                                    speakWithCallback(trelloResult)
                                                                    state = ControllerState.WaitingForCommand
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onError = { errMsg ->
                                                        runOnUiThread {
                                                            trelloResult = "Fehler beim Laden der Listen: $errMsg"
                                                            speakWithCallback(trelloResult)
                                                            state = ControllerState.WaitingForCommand
                                                        }
                                                    }
                                                )
                                            } else if (action == "add_list") {
                                                // Nur das Board fuzzy, dann neue Liste anlegen!
                                                val newListName = obj.optString("list")
                                                getAllLists(trelloKey, trelloToken, boardId, { lists ->
                                                    val listNames = lists.map { it.first }
                                                    if (listNames.any { it.equals(newListName, true) }) {
                                                        runOnUiThread {
                                                            trelloResult = "Die Liste '$newListName' existiert bereits."
                                                            speakWithCallback(trelloResult)
                                                            state = ControllerState.WaitingForCommand
                                                        }
                                                    } else {
                                                        addListToBoard(
                                                            trelloKey, trelloToken, boardId, newListName,
                                                            onSuccess = {
                                                                runOnUiThread {
                                                                    trelloResult = "Die Liste '$newListName' wurde im Board '${usedBoard ?: obj.optString("board")}' erstellt."
                                                                    speakWithCallback(trelloResult)
                                                                    state = ControllerState.Finished
                                                                }
                                                            },
                                                            onError = { errMsg ->
                                                                runOnUiThread {
                                                                    trelloResult = "Fehler beim Erstellen der Liste: $errMsg"
                                                                    speakWithCallback(trelloResult)
                                                                    state = ControllerState.WaitingForCommand
                                                                }
                                                            }
                                                        )
                                                    }
                                                }, onError = { errMsg ->
                                                    runOnUiThread {
                                                        trelloResult = "Fehler beim Laden der Listen: $errMsg"
                                                        speakWithCallback(trelloResult)
                                                        state = ControllerState.WaitingForCommand
                                                    }
                                                })
                                            } else {
                                                runOnUiThread {
                                                    trelloResult = "Diese Aktion ist noch nicht fertig implementiert."
                                                    speakWithCallback(trelloResult)
                                                    state = ControllerState.WaitingForCommand
                                                }
                                            }
                                        }
                                    },
                                    onError = { errMsg ->
                                        runOnUiThread {
                                            trelloResult = "Fehler beim Laden der Boards: $errMsg"
                                            speakWithCallback(trelloResult)
                                            state = ControllerState.WaitingForCommand
                                        }
                                    }
                                )
                            }
                        } else {
                            speakWithCallback("Okay, was soll ich stattdessen tun?") {
                                startSpeechInput("Was möchtest du tun?")
                            }
                            state = ControllerState.WaitingForCommand
                        }
                    }

                    ControllerState.Finished -> {
                        spokenText = ""
                        aiResult = ""
                        trelloResult = ""
                        state = ControllerState.WaitingForCommand
                    }
                    else -> {}
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
                        // Speak-Button als Card
                        Card(
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { startSpeechInput() },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(56.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Sprich jetzt",
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Sprich jetzt", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }

                        // Statusanzeige als Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    "Status: $state",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("🗣️ Erkannt:", style = MaterialTheme.typography.labelMedium)
                                Text(spokenText, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (actionJson != null) {
                                    Text("🤖 KI-Interpretation:", style = MaterialTheme.typography.labelMedium)
                                    Text(buildConfirmationText(actionJson!!), style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
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
