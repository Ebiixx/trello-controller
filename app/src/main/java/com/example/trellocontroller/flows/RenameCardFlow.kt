package com.example.trellocontroller.flows

import org.json.JSONObject

class RenameCardFlow(private val dependencies: RenameCardFlowDependencies) {

    private enum class State {
        IDLE,
        WAITING_FOR_BOARD_NAME,
        CONFIRM_BOARD_NOT_FOUND,
        WAITING_FOR_LIST_NAME,
        CONFIRM_LIST_NOT_FOUND,
        WAITING_FOR_OLD_CARD_NAME,
        CONFIRM_OLD_CARD_NOT_FOUND, // Or to select if multiple cards with same name
        WAITING_FOR_NEW_CARD_NAME,
        CONFIRM_RENAMING
    }

    private var currentState: State = State.IDLE

    private var currentBoardId: String? = null
    private var currentBoardName: String? = null
    private var currentListId: String? = null
    private var currentListName: String? = null
    private var currentOldCardId: String? = null
    private var currentOldCardName: String? = null
    private var currentNewCardName: String? = null

    private var pendingBoardNameFromAI: String? = null
    private var pendingListNameFromAI: String? = null
    private var pendingOldCardNameFromAI: String? = null
    private var pendingNewCardNameFromAI: String? = null

    fun start(initialDataFromAI: JSONObject?) {
        resetState()
        currentState = State.WAITING_FOR_BOARD_NAME

        pendingBoardNameFromAI = initialDataFromAI?.optString("board")?.takeIf { it.isNotBlank() }
        pendingListNameFromAI = initialDataFromAI?.optString("list")?.takeIf { it.isNotBlank() } // AI might confuse list with board sometimes
        pendingOldCardNameFromAI = initialDataFromAI?.optString("title")?.takeIf { it.isNotBlank() } // 'title' is often used for card name
        pendingNewCardNameFromAI = initialDataFromAI?.optString("new_title")?.takeIf { it.isNotBlank() } // Or 'new_name', 'destination_title' etc.

        dependencies.updateUiContext(buildContextText())

        if (pendingBoardNameFromAI != null) {
            handleBoardNameInput(pendingBoardNameFromAI!!)
        } else {
            dependencies.speakWithCallback("In welchem Board befindet sich die Karte, die du umbenennen möchtest?") {
                dependencies.startSpeechInput("Bitte nenne das Board")
            }
        }
    }

    fun handleSpokenInput(input: String) {
        dependencies.updateUiContext(buildContextText())
        when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> handleBoardNameInput(input)
            State.CONFIRM_BOARD_NOT_FOUND -> handleBoardNotFoundConfirmation(input)
            State.WAITING_FOR_LIST_NAME -> handleListNameInput(input)
            State.CONFIRM_LIST_NOT_FOUND -> handleListNotFoundConfirmation(input)
            State.WAITING_FOR_OLD_CARD_NAME -> handleOldCardNameInput(input)
            State.CONFIRM_OLD_CARD_NOT_FOUND -> handleOldCardNotFoundConfirmation(input)
            State.WAITING_FOR_NEW_CARD_NAME -> handleNewCardNameInput(input)
            State.CONFIRM_RENAMING -> handleConfirmRenaming(input)
            State.IDLE -> { /* Should not happen */ }
        }
    }

    private fun handleBoardNameInput(boardNameInput: String) {
        dependencies.getBestMatchingBoardId(boardNameInput,
            onResult = { boardId, matchedBoardName ->
                if (boardId != null) {
                    currentBoardId = boardId
                    currentBoardName = matchedBoardName
                    dependencies.updateUiContext(buildContextText())
                    proceedToAskListName()
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

    private fun proceedToAskListName() {
        currentState = State.WAITING_FOR_LIST_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingListNameFromAI != null) {
            handleListNameInput(pendingListNameFromAI!!)
            pendingListNameFromAI = null
        } else {
            dependencies.speakWithCallback("In welcher Liste auf dem Board '$currentBoardName' ist die Karte?") {
                dependencies.startSpeechInput("Bitte nenne die Liste")
            }
        }
    }

    private fun handleListNameInput(listNameInput: String) {
        val boardId = currentBoardId ?: return flowError("Board ID fehlt.")
        dependencies.getBestMatchingListId(boardId, listNameInput,
            onResult = { listId, matchedListName ->
                if (listId != null) {
                    currentListId = listId
                    currentListName = matchedListName
                    dependencies.updateUiContext(buildContextText())
                    proceedToAskOldCardName()
                } else {
                    dependencies.speakWithCallback("Die Liste '$listNameInput' im Board '$currentBoardName' wurde nicht gefunden. Soll ich dir mögliche Listen nennen?") {
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
        val boardId = currentBoardId ?: return flowError("Board ID fehlt.")
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

    private fun proceedToAskOldCardName() {
        currentState = State.WAITING_FOR_OLD_CARD_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingOldCardNameFromAI != null) {
            handleOldCardNameInput(pendingOldCardNameFromAI!!)
            pendingOldCardNameFromAI = null
        } else {
            dependencies.speakWithCallback("Wie lautet der aktuelle Name der Karte, die du umbenennen möchtest?") {
                dependencies.startSpeechInput("Bitte nenne den aktuellen Kartennamen")
            }
        }
    }

    private fun handleOldCardNameInput(oldCardNameInput: String) {
        val listId = currentListId ?: return flowError("Listen ID fehlt.")
        dependencies.getCardsFromList(listId,
            onResult = { cards ->
                val matchingCards = cards.filter { it.first.equals(oldCardNameInput, ignoreCase = true) }
                when (matchingCards.size) {
                    1 -> {
                        currentOldCardId = matchingCards.first().second
                        currentOldCardName = matchingCards.first().first // Use the exact name from Trello
                        dependencies.updateUiContext(buildContextText())
                        proceedToAskNewCardName()
                    }
                    0 -> {
                        dependencies.speakWithCallback("Ich konnte keine Karte mit dem Namen '$oldCardNameInput' in der Liste '$currentListName' finden. Möchtest du es erneut versuchen oder eine andere Karte suchen?") {
                            dependencies.startSpeechInput("Erneut versuchen oder andere Karte?")
                        }
                        currentState = State.CONFIRM_OLD_CARD_NOT_FOUND
                    }
                    else -> {
                        // Multiple cards found, for now, ask to be more specific or pick first.
                        // Future: could list them and ask to pick.
                        currentOldCardId = matchingCards.first().second
                        currentOldCardName = matchingCards.first().first
                        dependencies.speakWithCallback("Ich habe mehrere Karten mit dem Namen '$oldCardNameInput' gefunden. Ich nehme die erste. Wie soll die Karte '$currentOldCardName' neu heißen?") {
                           dependencies.startSpeechInput("Bitte nenne den neuen Kartennamen")
                        }
                        currentState = State.WAITING_FOR_NEW_CARD_NAME
                    }
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler beim Abrufen der Karten: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne den aktuellen Kartennamen")
                }
            }
        )
    }

    private fun handleOldCardNotFoundConfirmation(input: String) {
        if (input.lowercase().contains("erneut") || input.lowercase().contains("ja")) {
            currentState = State.WAITING_FOR_OLD_CARD_NAME
            dependencies.speakWithCallback("Wie lautet der aktuelle Name der Karte?") {
                dependencies.startSpeechInput("Bitte nenne den aktuellen Kartennamen")
            }
        } else {
            dependencies.speakWithCallback("Okay, Vorgang abgebrochen.") {
                dependencies.onFlowComplete("Kartenumbenennung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun proceedToAskNewCardName() {
        currentState = State.WAITING_FOR_NEW_CARD_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingNewCardNameFromAI != null) {
            handleNewCardNameInput(pendingNewCardNameFromAI!!)
            pendingNewCardNameFromAI = null
        } else {
            dependencies.speakWithCallback("Wie soll die Karte '$currentOldCardName' neu heißen?") {
                dependencies.startSpeechInput("Bitte nenne den neuen Kartennamen")
            }
        }
    }

    private fun handleNewCardNameInput(newCardNameInput: String) {
        currentNewCardName = newCardNameInput
        dependencies.updateUiContext(buildContextText())
        val confirmMsg = "Soll die Karte '$currentOldCardName' in '$currentNewCardName' umbenannt werden (Liste: '$currentListName', Board: '$currentBoardName')?"
        dependencies.speakWithCallback(confirmMsg) {
            dependencies.startSpeechInput("Ja oder Nein?")
        }
        currentState = State.CONFIRM_RENAMING
    }

    private fun handleConfirmRenaming(input: String) {
        if (input.lowercase().contains("ja")) {
            val cardId = currentOldCardId ?: return flowError("Karten ID fehlt.")
            val newName = currentNewCardName ?: return flowError("Neuer Kartenname fehlt.")

            dependencies.renameCardOnTrello(cardId, newName,
                onSuccess = {
                    val successMsg = "Karte '$currentOldCardName' wurde erfolgreich in '$newName' umbenannt."
                    dependencies.onFlowComplete(successMsg, true)
                    resetState()
                },
                onError = { errMsg ->
                    dependencies.onFlowComplete("Fehler beim Umbenennen der Karte: $errMsg", false)
                    resetState() // Or ask to retry
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Karte nicht umbenannt.") {
                dependencies.onFlowComplete("Kartenumbenennung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun flowError(message: String) {
        dependencies.onFlowComplete("Interner Fehler im Umbenennungsflow: $message", false)
        resetState()
    }

    fun isActive(): Boolean = currentState != State.IDLE

    fun resetState() {
        currentState = State.IDLE
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentListName = null
        currentOldCardId = null
        currentOldCardName = null
        currentNewCardName = null
        pendingBoardNameFromAI = null
        pendingListNameFromAI = null
        pendingOldCardNameFromAI = null
        pendingNewCardNameFromAI = null
        dependencies.updateUiContext("")
    }

    private fun buildContextText(): String {
        val sb = StringBuilder("Aktion: Karte umbenennen")
        currentBoardName?.let { sb.append("\nBoard: $it") }
        currentListName?.let { sb.append("\nListe: $it") }
        currentOldCardName?.let { sb.append("\nAlte Karte: $it") }
        currentNewCardName?.let { sb.append("\nNeuer Name: $it") }

        val nextStep = when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> "Board-Name?"
            State.CONFIRM_BOARD_NOT_FOUND -> "Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_LIST_NAME -> "Listen-Name?"
            State.CONFIRM_LIST_NOT_FOUND -> "Liste nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_OLD_CARD_NAME -> "Aktueller Kartenname?"
            State.CONFIRM_OLD_CARD_NOT_FOUND -> "Karte nicht gefunden, erneut? (Ja/Nein)"
            State.WAITING_FOR_NEW_CARD_NAME -> "Neuer Kartenname?"
            State.CONFIRM_RENAMING -> "Umbennung bestätigen? (Ja/Nein)"
            State.IDLE -> ""
        }
        if (nextStep.isNotBlank()) sb.append("\nNächster Schritt: $nextStep")
        return sb.toString()
    }
}