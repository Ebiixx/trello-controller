package com.example.trellocontroller.flows

import org.json.JSONObject

class AddListFlow(private val dependencies: AddListFlowDependencies) {

    private enum class State {
        IDLE,
        WAITING_FOR_BOARD_NAME,
        CONFIRM_BOARD_NOT_FOUND,
        WAITING_FOR_NEW_LIST_NAME,
        CONFIRM_CREATION
    }

    private var currentState: State = State.IDLE

    private var currentBoardId: String? = null
    private var currentBoardName: String? = null
    private var currentNewListName: String? = null

    private var pendingBoardNameFromAI: String? = null
    private var pendingNewListNameFromAI: String? = null

    fun start(initialDataFromAI: JSONObject?) {
        resetState()
        currentState = State.WAITING_FOR_BOARD_NAME

        pendingBoardNameFromAI = initialDataFromAI?.optString("board")?.takeIf { it.isNotBlank() }
        // "list" im JSON von AI ist der Name der neuen Liste für "add_list"
        pendingNewListNameFromAI = initialDataFromAI?.optString("list")?.takeIf { it.isNotBlank() }

        dependencies.updateUiContext(buildContextText())

        if (pendingBoardNameFromAI != null) {
            handleBoardNameInput(pendingBoardNameFromAI!!)
        } else {
            dependencies.speakWithCallback("In welchem Board soll die neue Liste erstellt werden?") {
                dependencies.startSpeechInput("Bitte nenne das Board")
            }
        }
    }

    fun handleSpokenInput(input: String) {
        dependencies.updateUiContext(buildContextText())
        when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> handleBoardNameInput(input)
            State.CONFIRM_BOARD_NOT_FOUND -> handleBoardNotFoundConfirmation(input)
            State.WAITING_FOR_NEW_LIST_NAME -> handleNewListNameInput(input)
            State.CONFIRM_CREATION -> handleConfirmCreation(input)
            State.IDLE -> { /* Sollte nicht passieren */ }
        }
    }

    private fun handleBoardNameInput(boardNameInput: String) {
        dependencies.getBestMatchingBoardId(boardNameInput,
            onResult = { boardId, matchedBoardName ->
                if (boardId != null) {
                    currentBoardId = boardId
                    currentBoardName = matchedBoardName
                    dependencies.updateUiContext(buildContextText())
                    if (pendingNewListNameFromAI != null) {
                        currentState = State.WAITING_FOR_NEW_LIST_NAME
                        handleNewListNameInput(pendingNewListNameFromAI!!)
                        pendingNewListNameFromAI = null // Verbraucht
                    } else {
                        dependencies.speakWithCallback("Okay, im Board '$currentBoardName'. Wie soll die neue Liste heißen?") {
                            dependencies.startSpeechInput("Bitte nenne den Namen der neuen Liste")
                        }
                        currentState = State.WAITING_FOR_NEW_LIST_NAME
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
            }
        )
    }

    private fun handleBoardNotFoundConfirmation(input: String) {
        if (input.lowercase().contains("ja")) {
            dependencies.getAllBoards(
                onResult = { boards ->
                    val boardNames = boards.map { it.first }
                    val text = if (boardNames.isNotEmpty()) {
                        "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. In welchem Board soll die Liste erstellt werden?"
                    } else {
                        "Ich konnte keine Boards finden. Bitte nenne ein Board."
                    }
                    dependencies.speakWithCallback(text) { dependencies.startSpeechInput("Bitte nenne das Board") }
                    currentState = State.WAITING_FOR_BOARD_NAME
                },
                onError = {
                    dependencies.speakWithCallback("Fehler beim Laden der Boards. Bitte nenne ein Board.") {
                        dependencies.startSpeechInput("Bitte nenne das Board")
                    }
                    currentState = State.WAITING_FOR_BOARD_NAME
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Vorgang abgebrochen.") {
                dependencies.onFlowComplete("Listenerstellung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun handleNewListNameInput(newListNameInput: String) {
        currentNewListName = newListNameInput
        dependencies.updateUiContext(buildContextText())
        val confirmMsg = "Ich werde eine neue Liste mit dem Namen '${currentNewListName}' im Board '${currentBoardName}' erstellen. Ist das richtig?"
        dependencies.speakWithCallback(confirmMsg) {
            dependencies.startSpeechInput("Ja oder Nein?")
        }
        currentState = State.CONFIRM_CREATION
    }

    private fun handleConfirmCreation(input: String) {
        if (input.lowercase().contains("ja")) {
            if (currentBoardId != null && currentNewListName != null && currentBoardName != null) {
                dependencies.addListToTrello(currentBoardId!!, currentNewListName!!,
                    onSuccess = {
                        val successMsg = "Liste '${currentNewListName}' wurde im Board '${currentBoardName}' erstellt."
                        dependencies.onFlowComplete(successMsg, true)
                        resetState()
                    },
                    onError = { errMsg ->
                        dependencies.onFlowComplete("Fehler beim Erstellen der Liste: $errMsg", false)
                        resetState()
                    }
                )
            } else {
                dependencies.onFlowComplete("Interner Fehler: Notwendige Informationen für Listenerstellung fehlen.", false)
                resetState()
            }
        } else {
            dependencies.speakWithCallback("Okay, Liste nicht erstellt.") {
                dependencies.onFlowComplete("Listenerstellung abgebrochen.", false)
                resetState()
            }
        }
    }

    fun isActive(): Boolean = currentState != State.IDLE

    fun resetState() {
        currentState = State.IDLE
        currentBoardId = null
        currentBoardName = null
        currentNewListName = null
        pendingBoardNameFromAI = null
        pendingNewListNameFromAI = null
        dependencies.updateUiContext("")
    }

    private fun buildContextText(): String {
        val sb = StringBuilder("Aktion: Liste erstellen")
        currentBoardName?.let { sb.append("\nBoard: $it") }
        currentNewListName?.let { sb.append("\nNeuer Listenname: $it") }

        val nextStep = when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> "Board-Name?"
            State.CONFIRM_BOARD_NOT_FOUND -> "Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_NEW_LIST_NAME -> "Name der neuen Liste?"
            State.CONFIRM_CREATION -> "Erstellung bestätigen? (Ja/Nein)"
            State.IDLE -> ""
        }
        if (nextStep.isNotBlank()) sb.append("\nNächster Schritt: $nextStep")
        return sb.toString()
    }
}