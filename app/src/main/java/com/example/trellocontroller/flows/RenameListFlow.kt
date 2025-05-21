package com.example.trellocontroller.flows

import org.json.JSONObject

class RenameListFlow(private val dependencies: RenameListFlowDependencies) {

    private enum class State {
        IDLE,
        WAITING_FOR_BOARD_NAME,
        CONFIRM_BOARD_NOT_FOUND,
        WAITING_FOR_OLD_LIST_NAME,
        CONFIRM_OLD_LIST_NOT_FOUND,
        WAITING_FOR_NEW_LIST_NAME,
        CONFIRM_RENAMING
    }

    private var currentState: State = State.IDLE

    private var currentBoardId: String? = null
    private var currentBoardName: String? = null
    private var currentListId: String? = null
    private var currentOldListName: String? = null
    private var currentNewListName: String? = null

    private var pendingBoardNameFromAI: String? = null
    private var pendingOldListNameFromAI: String? = null
    private var pendingNewListNameFromAI: String? = null

    fun start(initialDataFromAI: JSONObject?) {
        resetState()
        currentState = State.WAITING_FOR_BOARD_NAME

        pendingBoardNameFromAI = initialDataFromAI?.optString("board")?.takeIf { it.isNotBlank() }
        // Annahme: AI liefert "list_name" für die umzubenennende Liste und "new_name" für den neuen Namen
        pendingOldListNameFromAI = initialDataFromAI?.optString("list_name")?.takeIf { it.isNotBlank() }
                                ?: initialDataFromAI?.optString("list")?.takeIf { it.isNotBlank() }
        pendingNewListNameFromAI = initialDataFromAI?.optString("new_name")?.takeIf { it.isNotBlank() }
                                ?: initialDataFromAI?.optString("new_list_name")?.takeIf { it.isNotBlank() }


        dependencies.updateUiContext(buildContextText())

        if (pendingBoardNameFromAI != null) {
            handleBoardNameInput(pendingBoardNameFromAI!!)
            pendingBoardNameFromAI = null
        } else {
            dependencies.speakWithCallback("Auf welchem Board befindet sich die Liste, die du umbenennen möchtest?") { // Korrigiert: "Liste" statt "Karte"
                dependencies.startSpeechInput("Bitte nenne das Board")
            }
        }
    }

    fun handleSpokenInput(input: String) {
        dependencies.updateUiContext(buildContextText())
        when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> handleBoardNameInput(input)
            State.CONFIRM_BOARD_NOT_FOUND -> handleBoardNotFoundConfirmation(input)
            State.WAITING_FOR_OLD_LIST_NAME -> handleOldListNameInput(input)
            State.CONFIRM_OLD_LIST_NOT_FOUND -> handleOldListNotFoundConfirmation(input)
            State.WAITING_FOR_NEW_LIST_NAME -> handleNewListNameInput(input)
            State.CONFIRM_RENAMING -> handleConfirmRenaming(input)
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
                    proceedToAskOldListName()
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
                        "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. Auf welchem Board befindet sich die Liste?" // Angepasst
                    } else {
                        "Ich konnte keine Boards finden. Bitte nenne das Board, auf dem sich die Liste befindet." // Angepasst
                    }
                    dependencies.speakWithCallback(text) { dependencies.startSpeechInput("Bitte nenne das Board") }
                    currentState = State.WAITING_FOR_BOARD_NAME
                },
                onError = {
                    dependencies.speakWithCallback("Fehler beim Laden der Boards. Bitte nenne das Board, auf dem sich die Liste befindet.") { // Angepasst
                        dependencies.startSpeechInput("Bitte nenne das Board")
                    }
                    currentState = State.WAITING_FOR_BOARD_NAME
                }
            )
        } else {
            currentState = State.WAITING_FOR_BOARD_NAME
            dependencies.updateUiContext(buildContextText())
            dependencies.speakWithCallback("Okay. Bitte wiederhole den Namen des Boards, auf dem sich die Liste befindet.") { // Angepasst
                dependencies.startSpeechInput("Bitte nenne den Namen für das Board.")
            }
        }
    }

    private fun proceedToAskOldListName() {
        currentState = State.WAITING_FOR_OLD_LIST_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingOldListNameFromAI != null) {
            handleOldListNameInput(pendingOldListNameFromAI!!)
            pendingOldListNameFromAI = null
        } else {
            dependencies.speakWithCallback("Welche Liste auf dem Board '$currentBoardName' möchtest du umbenennen?") { // Dieser Text ist bereits korrekt
                dependencies.startSpeechInput("Bitte nenne die Liste")
            }
        }
    }

    private fun handleOldListNameInput(listNameInput: String) {
        val boardId = currentBoardId ?: return flowError("Board ID fehlt.")
        dependencies.getBestMatchingListId(boardId, listNameInput,
            onResult = { listId, matchedListName ->
                if (listId != null) {
                    currentListId = listId
                    currentOldListName = matchedListName
                    dependencies.updateUiContext(buildContextText())
                    proceedToAskNewListName()
                } else {
                    dependencies.speakWithCallback("Die Liste '$listNameInput' im Board '$currentBoardName' wurde nicht gefunden. Soll ich dir mögliche Listen nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_OLD_LIST_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Listensuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne die Liste")
                }
            }
        )
    }

     private fun handleOldListNotFoundConfirmation(input: String) {
        val boardId = currentBoardId ?: return flowError("Board ID fehlt.")
        if (input.lowercase().contains("ja")) {
            dependencies.getAllLists(boardId,
                onResult = { lists ->
                    val listNames = lists.map { it.first }
                    val text = if (listNames.isNotEmpty()) {
                        "Mögliche Listen sind: ${listNames.joinToString(", ", limit = 5)}. Welche Liste?"
                    } else {
                        "Ich konnte keine Listen im Board '$currentBoardName' finden. Bitte nenne eine Liste."
                    }
                    dependencies.speakWithCallback(text) { dependencies.startSpeechInput("Bitte nenne die Liste") }
                    currentState = State.WAITING_FOR_OLD_LIST_NAME
                },
                onError = {
                    dependencies.speakWithCallback("Fehler beim Laden der Listen. Bitte nenne die Liste.") {
                        dependencies.startSpeechInput("Bitte nenne die Liste")
                    }
                    currentState = State.WAITING_FOR_OLD_LIST_NAME
                }
            )
        } else {
            currentState = State.WAITING_FOR_OLD_LIST_NAME
            dependencies.updateUiContext(buildContextText())
            dependencies.speakWithCallback("Okay. Bitte wiederhole den Namen der Liste, die umbenannt werden soll.") {
                dependencies.startSpeechInput("Bitte nenne den Namen der Liste.")
            }
        }
    }

    private fun proceedToAskNewListName() {
        currentState = State.WAITING_FOR_NEW_LIST_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingNewListNameFromAI != null) {
            handleNewListNameInput(pendingNewListNameFromAI!!)
            pendingNewListNameFromAI = null
        } else {
            dependencies.speakWithCallback("Wie soll die Liste '$currentOldListName' neu heißen?") {
                dependencies.startSpeechInput("Bitte nenne den neuen Listennamen")
            }
        }
    }

    private fun handleNewListNameInput(newListName: String) {
        if (newListName.isBlank()) {
            dependencies.speakWithCallback("Der neue Listenname darf nicht leer sein. Bitte nenne einen gültigen Namen.") {
                dependencies.startSpeechInput("Bitte nenne den neuen Listennamen")
            }
            return
        }
        currentNewListName = newListName
        dependencies.updateUiContext(buildContextText())
        proceedToConfirmRenaming()
    }

    private fun proceedToConfirmRenaming() {
        currentState = State.CONFIRM_RENAMING
        dependencies.updateUiContext(buildContextText())
        val confirmMsg = "Soll die Liste '$currentOldListName' wirklich in '$currentNewListName' umbenannt werden (Board: '$currentBoardName')?"
        dependencies.speakWithCallback(confirmMsg) {
            dependencies.startSpeechInput("Ja oder Nein?")
        }
    }

    private fun handleConfirmRenaming(input: String) {
        if (input.lowercase().contains("ja")) {
            val listId = currentListId ?: return flowError("Listen ID fehlt.")
            val newName = currentNewListName ?: return flowError("Neuer Listenname fehlt.")

            dependencies.renameListOnTrello(listId, newName,
                onSuccess = {
                    val successMsg = "Liste '$currentOldListName' wurde erfolgreich in '$newName' umbenannt."
                    dependencies.onFlowComplete(successMsg, true)
                    resetState()
                },
                onError = { errMsg ->
                    dependencies.onFlowComplete("Fehler beim Umbenennen der Liste: $errMsg", false)
                    resetState()
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Liste nicht umbenannt.") {
                dependencies.onFlowComplete("Umbenennung der Liste abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun flowError(message: String) {
        dependencies.onFlowComplete("Interner Fehler im Umbenennungs-Flow: $message", false)
        resetState()
    }

    fun isActive(): Boolean = currentState != State.IDLE

    fun resetState() {
        currentState = State.IDLE
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentOldListName = null
        currentNewListName = null
        pendingBoardNameFromAI = null
        pendingOldListNameFromAI = null
        pendingNewListNameFromAI = null
        dependencies.updateUiContext("")
    }

    private fun buildContextText(): String {
        val sb = StringBuilder("Aktion: Liste umbenennen")
        currentBoardName?.let { sb.append("\nBoard: $it") }
        currentOldListName?.let { sb.append("\nAlte Liste: $it") }
        currentNewListName?.let { sb.append("\nNeue Liste: $it") }

        val nextStep = when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> "Board-Name?"
            State.CONFIRM_BOARD_NOT_FOUND -> "Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_OLD_LIST_NAME -> "Alter Listen-Name?"
            State.CONFIRM_OLD_LIST_NOT_FOUND -> "Liste nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_NEW_LIST_NAME -> "Neuer Listen-Name?"
            State.CONFIRM_RENAMING -> "Umbenennung bestätigen? (Ja/Nein)"
            State.IDLE -> ""
        }
        if (nextStep.isNotBlank()) sb.append("\nNächster Schritt: $nextStep")
        return sb.toString()
    }
}