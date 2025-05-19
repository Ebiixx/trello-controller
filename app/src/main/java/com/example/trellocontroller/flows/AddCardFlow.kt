// filepath: app/src/main/java/com/example/trellocontroller/flows/AddCardFlow.kt
package com.example.trellocontroller.flows

import com.example.trellocontroller.BuildConfig
import org.json.JSONObject

/**
 * Interface für Abhängigkeiten, die der AddCardFlow von der MainActivity benötigt.
 * Dies hilft, den Flow von der MainActivity zu entkoppeln.
 */
interface AddCardFlowDependencies {
    val trelloKey: String
    val trelloToken: String

    fun speakWithCallback(text: String, onDone: (() -> Unit)? = null)
    fun startSpeechInput(prompt: String)

    fun getBestMatchingBoardId(
        boardName: String,
        onResult: (boardId: String?, matchedBoardName: String?) -> Unit,
        onError: (String) -> Unit
    )

    fun getAllBoards(
        onResult: (List<Pair<String, String>>) -> Unit, // List of (BoardName, BoardId)
        onError: (String) -> Unit
    )

    fun getBestMatchingListId(
        boardId: String,
        listName: String,
        onResult: (listId: String?, matchedListName: String?) -> Unit,
        onError: (String) -> Unit
    )

    fun getAllLists(
        boardId: String,
        onResult: (List<Pair<String, String>>) -> Unit, // List of (ListName, ListId)
        onError: (String) -> Unit
    )

    fun addCardToTrello(
        listId: String,
        cardName: String,
        cardDesc: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    // Callback an MainActivity, wenn der Flow abgeschlossen ist (erfolgreich oder nicht)
    fun onFlowComplete(message: String, success: Boolean)

    // Callback, um den aktuellen Kontext des Flows in der UI anzuzeigen
    fun updateUiContext(context: String)
}

class AddCardFlow(private val dependencies: AddCardFlowDependencies) {

    private enum class State {
        IDLE,
        WAITING_FOR_BOARD_NAME,
        CONFIRM_BOARD_NOT_FOUND,
        WAITING_FOR_LIST_NAME,
        CONFIRM_LIST_NOT_FOUND,
        WAITING_FOR_CARD_TITLE,
        CONFIRM_CREATION
    }

    private var currentState: State = State.IDLE

    private var currentBoardId: String? = null
    private var currentBoardName: String? = null
    private var currentListId: String? = null
    private var currentListName: String? = null
    private var currentCardTitle: String? = null

    private var pendingBoardNameFromAI: String? = null
    private var pendingListNameFromAI: String? = null
    private var pendingCardTitleFromAI: String? = null

    fun start(initialDataFromAI: JSONObject?) {
        resetState()
        currentState = State.WAITING_FOR_BOARD_NAME

        pendingBoardNameFromAI = initialDataFromAI?.optString("board")?.takeIf { it.isNotBlank() }
        pendingListNameFromAI = initialDataFromAI?.optString("list")?.takeIf { it.isNotBlank() }
        pendingCardTitleFromAI = initialDataFromAI?.optString("title")?.takeIf { it.isNotBlank() }

        dependencies.updateUiContext(buildContextText())

        if (pendingBoardNameFromAI != null) {
            handleBoardNameInput(pendingBoardNameFromAI!!)
        } else {
            dependencies.speakWithCallback("In welchem Board soll die Karte erstellt werden?") {
                dependencies.startSpeechInput("Bitte nenne das Board")
            }
        }
    }

    fun handleSpokenInput(input: String) {
        dependencies.updateUiContext(buildContextText()) // Update context before processing
        when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> handleBoardNameInput(input)
            State.CONFIRM_BOARD_NOT_FOUND -> handleBoardNotFoundConfirmation(input)
            State.WAITING_FOR_LIST_NAME -> handleListNameInput(input)
            State.CONFIRM_LIST_NOT_FOUND -> handleListNotFoundConfirmation(input)
            State.WAITING_FOR_CARD_TITLE -> handleCardTitleInput(input)
            State.CONFIRM_CREATION -> handleConfirmCreation(input)
            State.IDLE -> { /* Sollte nicht passieren, wenn der Flow aktiv ist */ }
        }
    }

    private fun handleBoardNameInput(boardNameInput: String) { // boardNameInput ist bereits normalisiert
        dependencies.getBestMatchingBoardId(boardNameInput, // Aufruf der "Best Match"-Logik
            onResult = { boardId, matchedBoardName ->
                if (boardId != null) {
                    currentBoardId = boardId
                    currentBoardName = matchedBoardName
                    dependencies.updateUiContext(buildContextText())
                    if (pendingListNameFromAI != null) {
                        currentState = State.WAITING_FOR_LIST_NAME
                        handleListNameInput(pendingListNameFromAI!!)
                        pendingListNameFromAI = null // Verbraucht
                    } else {
                        dependencies.speakWithCallback("Okay, im Board '$currentBoardName'. In welcher Liste soll die Karte erstellt werden?") {
                            dependencies.startSpeechInput("Bitte nenne die Liste")
                        }
                        currentState = State.WAITING_FOR_LIST_NAME
                    }
                } else {
                    dependencies.speakWithCallback("Das Board '$boardNameInput' wurde nicht gefunden. Soll ich dir mögliche Boards nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_BOARD_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Boardsuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne das Board")
                }
                // Bleibe im aktuellen Zustand, um erneute Eingabe zu ermöglichen
            }
        )
    }

    private fun handleBoardNotFoundConfirmation(input: String) {
        if (input.lowercase().contains("ja")) {
            dependencies.getAllBoards(
                onResult = { boards ->
                    val boardNames = boards.map { it.first }
                    val text = if (boardNames.isNotEmpty()) {
                        "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. In welchem Board?"
                    } else {
                        "Ich konnte keine Boards finden. Bitte nenne ein Board."
                    }
                    dependencies.speakWithCallback(text) { dependencies.startSpeechInput("Bitte nenne das Board") }
                    currentState = State.WAITING_FOR_BOARD_NAME
                },
                onError = {
                    val errorPrompt = "Bitte nenne das Board"
                    dependencies.speakWithCallback("Fehler beim Laden der Boards. $errorPrompt.") {
                        dependencies.startSpeechInput(errorPrompt)
                    }
                    currentState = State.WAITING_FOR_BOARD_NAME
                }
            )
        } else { // User said "Nein" to listing boards
            val prompt = "Okay. Bitte wiederhole den Namen für das Board."
            currentState = State.WAITING_FOR_BOARD_NAME
            dependencies.updateUiContext(buildContextText())
            dependencies.speakWithCallback(prompt) {
                dependencies.startSpeechInput("Bitte nenne den Namen für das Board.")
            }
        }
    }

    private fun handleListNameInput(listNameInput: String) {
        val boardId = currentBoardId ?: return dependencies.onFlowComplete("Interner Fehler: Board ID fehlt.", false).also { resetState() }

        dependencies.getBestMatchingListId(boardId, listNameInput,
            onResult = { listId, matchedListName ->
                if (listId != null) {
                    currentListId = listId
                    currentListName = matchedListName
                    dependencies.updateUiContext(buildContextText())
                    if (pendingCardTitleFromAI != null) {
                        currentState = State.WAITING_FOR_CARD_TITLE
                        handleCardTitleInput(pendingCardTitleFromAI!!)
                        pendingCardTitleFromAI = null // Verbraucht
                    } else {
                        dependencies.speakWithCallback("Okay, in Liste '$currentListName'. Wie soll die Karte heißen?") {
                            dependencies.startSpeechInput("Bitte nenne den Titel der Karte")
                        }
                        currentState = State.WAITING_FOR_CARD_TITLE
                    }
                } else {
                    dependencies.speakWithCallback("Die Liste '$listNameInput' wurde im Board '$currentBoardName' nicht gefunden. Soll ich dir mögliche Listen nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_LIST_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Listensuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne die Liste")
                }
            }
        )
    }

    private fun handleListNotFoundConfirmation(input: String) {
        val boardId = currentBoardId ?: return dependencies.onFlowComplete("Interner Fehler: Board ID fehlt.", false).also { resetState() }
        if (input.lowercase().contains("ja")) {
            dependencies.getAllLists(boardId,
                onResult = { lists ->
                    val listNames = lists.map { it.first }
                    val text = if (listNames.isNotEmpty()) {
                        "Mögliche Listen sind: ${listNames.joinToString(", ", limit = 5)}. In welcher Liste?"
                    } else {
                        "Ich konnte keine Listen im Board '$currentBoardName' finden. Bitte nenne eine Liste."
                    }
                    dependencies.speakWithCallback(text) { dependencies.startSpeechInput("Bitte nenne die Liste") }
                    currentState = State.WAITING_FOR_LIST_NAME
                },
                onError = {
                    val errorPrompt = "Bitte nenne die Liste"
                    dependencies.speakWithCallback("Fehler beim Laden der Listen. $errorPrompt.") {
                        dependencies.startSpeechInput(errorPrompt)
                    }
                    currentState = State.WAITING_FOR_LIST_NAME
                }
            )
        } else { // User said "Nein" to listing lists
            val prompt = "Okay. Bitte wiederhole den Namen für die Liste."
            currentState = State.WAITING_FOR_LIST_NAME
            dependencies.updateUiContext(buildContextText())
            dependencies.speakWithCallback(prompt) {
                dependencies.startSpeechInput("Bitte nenne den Namen für die Liste.")
            }
        }
    }

    private fun handleCardTitleInput(cardTitleInput: String) {
        currentCardTitle = cardTitleInput
        dependencies.updateUiContext(buildContextText())
        val confirmMsg = "Ich werde eine neue Karte mit dem Titel '${currentCardTitle}' in der Liste '${currentListName}' im Board '${currentBoardName}' erstellen. Möchtest du das tun?"
        dependencies.speakWithCallback(confirmMsg) {
            dependencies.startSpeechInput("Ja oder Nein?")
        }
        currentState = State.CONFIRM_CREATION
    }

    private fun handleConfirmCreation(input: String) {
        if (input.lowercase().contains("ja")) {
            if (currentListId != null && currentCardTitle != null && currentBoardName != null && currentListName != null) {
                dependencies.addCardToTrello(currentListId!!, currentCardTitle!!, "", // Beschreibung vorerst leer
                    onSuccess = {
                        val successMsg = "Karte '${currentCardTitle}' in Liste '${currentListName}' im Board '${currentBoardName}' erstellt."
                        dependencies.onFlowComplete(successMsg, true)
                        resetState()
                    },
                    onError = { errMsg ->
                        dependencies.onFlowComplete("Fehler beim Erstellen der Karte: $errMsg", false)
                        resetState()
                    }
                )
            } else {
                dependencies.onFlowComplete("Interner Fehler: Notwendige Informationen für Kartenerstellung fehlen.", false)
                resetState()
            }
        } else {
            dependencies.speakWithCallback("Okay, Karte nicht erstellt.") {
                dependencies.onFlowComplete("Karten-Erstellung abgebrochen.", false)
                resetState()
            }
        }
    }

    fun isActive(): Boolean = currentState != State.IDLE

    fun resetState() {
        currentState = State.IDLE
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentListName = null
        currentCardTitle = null
        pendingBoardNameFromAI = null
        pendingListNameFromAI = null
        pendingCardTitleFromAI = null
        dependencies.updateUiContext("") // Kontext in der UI zurücksetzen
    }

    private fun buildContextText(): String {
        val sb = StringBuilder("Aktion: Karte erstellen")
        currentBoardName?.let { sb.append("\nBoard: $it") }
        currentListName?.let { sb.append("\nZielliste: $it") }
        currentCardTitle?.let { sb.append("\nKartentitel: $it") }

        val nextStep = when(currentState) {
            State.WAITING_FOR_BOARD_NAME -> "Board-Name?"
            State.CONFIRM_BOARD_NOT_FOUND -> "Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_LIST_NAME -> "Listen-Name?"
            State.CONFIRM_LIST_NOT_FOUND -> "Liste nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_CARD_TITLE -> "Karten-Titel?"
            State.CONFIRM_CREATION -> "Erstellung bestätigen? (Ja/Nein)"
            State.IDLE -> ""
        }
        if (nextStep.isNotBlank()) sb.append("\nNächster Schritt: $nextStep")
        return sb.toString()
    }
}