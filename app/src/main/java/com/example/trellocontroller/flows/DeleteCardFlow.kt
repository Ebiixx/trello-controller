package com.example.trellocontroller.flows

import org.json.JSONObject

class DeleteCardFlow(private val dependencies: DeleteCardFlowDependencies) {

    private enum class State {
        IDLE,
        WAITING_FOR_BOARD_NAME,
        CONFIRM_BOARD_NOT_FOUND,
        WAITING_FOR_LIST_NAME,
        CONFIRM_LIST_NOT_FOUND,
        WAITING_FOR_CARD_NAME,
        CONFIRM_CARD_NOT_FOUND,
        CONFIRM_DELETION
    }

    private var currentState: State = State.IDLE

    private var currentBoardId: String? = null
    private var currentBoardName: String? = null
    private var currentListId: String? = null
    private var currentListName: String? = null
    private var currentCardId: String? = null
    private var currentCardName: String? = null

    private var pendingBoardNameFromAI: String? = null
    private var pendingListNameFromAI: String? = null
    private var pendingCardNameFromAI: String? = null

    fun start(initialDataFromAI: JSONObject?) {
        resetState()
        currentState = State.WAITING_FOR_BOARD_NAME

        pendingBoardNameFromAI = initialDataFromAI?.optString("board")?.takeIf { it.isNotBlank() }
        pendingListNameFromAI = initialDataFromAI?.optString("list")?.takeIf { it.isNotBlank() }
        pendingCardNameFromAI = initialDataFromAI?.optString("title")?.takeIf { it.isNotBlank() } // "title" is used for card name

        dependencies.updateUiContext(buildContextText())

        if (pendingBoardNameFromAI != null) {
            handleBoardNameInput(pendingBoardNameFromAI!!)
            pendingBoardNameFromAI = null
        } else {
            dependencies.speakWithCallback("In welchem Board befindet sich die Karte, die du löschen möchtest?") {
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
            State.WAITING_FOR_CARD_NAME -> handleCardNameInput(input)
            State.CONFIRM_CARD_NOT_FOUND -> handleCardNotFoundConfirmation(input)
            State.CONFIRM_DELETION -> handleConfirmDeletion(input)
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
        } else {
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
                    proceedToAskCardName()
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
        } else {
            val prompt = "Okay. Bitte wiederhole den Namen für die Liste."
            currentState = State.WAITING_FOR_LIST_NAME
            dependencies.updateUiContext(buildContextText())
            dependencies.speakWithCallback(prompt) {
                dependencies.startSpeechInput("Bitte nenne den Namen für die Liste.")
            }
        }
    }

    private fun proceedToAskCardName() {
        currentState = State.WAITING_FOR_CARD_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingCardNameFromAI != null) {
            handleCardNameInput(pendingCardNameFromAI!!)
            pendingCardNameFromAI = null
        } else {
            dependencies.speakWithCallback("Wie heißt die Karte, die du löschen möchtest?") {
                dependencies.startSpeechInput("Bitte nenne den Kartennamen")
            }
        }
    }

    private fun handleCardNameInput(cardNameInput: String) {
        val listId = currentListId ?: return flowError("Listen ID fehlt.")
        dependencies.getCardsFromList(listId,
            onResult = { cards ->
                val matchingCards = cards.filter { it.first.equals(cardNameInput, ignoreCase = true) }
                when (matchingCards.size) {
                    1 -> {
                        currentCardId = matchingCards.first().second
                        currentCardName = matchingCards.first().first
                        dependencies.updateUiContext(buildContextText())
                        proceedToConfirmDeletion()
                    }
                    0 -> {
                        dependencies.speakWithCallback("Ich konnte keine Karte mit dem Namen '$cardNameInput' in der Liste '$currentListName' finden. Möchtest du es erneut versuchen?") {
                            dependencies.startSpeechInput("Erneut versuchen oder abbrechen?")
                        }
                        currentState = State.CONFIRM_CARD_NOT_FOUND
                    }
                    else -> {
                        // Mehrere Karten gefunden, nimm die erste und frage nach Bestätigung
                        currentCardId = matchingCards.first().second
                        currentCardName = matchingCards.first().first
                        dependencies.speakWithCallback("Ich habe mehrere Karten mit dem Namen '$cardNameInput' gefunden. Ich nehme die erste: '$currentCardName'. Möchtest du diese löschen?") {
                            dependencies.startSpeechInput("Ja oder Nein?")
                        }
                        currentState = State.CONFIRM_DELETION // Direkt zur Bestätigung, da wir eine Karte ausgewählt haben
                    }
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler beim Abrufen der Karten: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne den Kartennamen")
                }
            }
        )
    }

    private fun handleCardNotFoundConfirmation(input: String) {
        if (input.lowercase().contains("erneut") || input.lowercase().contains("ja")) {
            currentState = State.WAITING_FOR_CARD_NAME
            dependencies.speakWithCallback("Wie heißt die Karte, die du löschen möchtest?") {
                dependencies.startSpeechInput("Bitte nenne den Kartennamen")
            }
        } else {
            dependencies.speakWithCallback("Okay, Vorgang abgebrochen.") {
                dependencies.onFlowComplete("Kartenlöschung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun proceedToConfirmDeletion() {
        currentState = State.CONFIRM_DELETION
        dependencies.updateUiContext(buildContextText())
        val confirmMsg = "Soll die Karte '$currentCardName' wirklich endgültig gelöscht werden (Liste: '$currentListName', Board: '$currentBoardName')? Diese Aktion kann nicht rückgängig gemacht werden."
        dependencies.speakWithCallback(confirmMsg) {
            dependencies.startSpeechInput("Ja oder Nein?")
        }
    }

    private fun handleConfirmDeletion(input: String) {
        if (input.lowercase().contains("ja")) {
            val cardId = currentCardId ?: return flowError("Karten ID fehlt.")
            dependencies.deleteCardOnTrello(cardId,
                onSuccess = {
                    val successMsg = "Karte '$currentCardName' wurde erfolgreich gelöscht."
                    dependencies.onFlowComplete(successMsg, true)
                    resetState()
                },
                onError = { errMsg ->
                    dependencies.onFlowComplete("Fehler beim Löschen der Karte: $errMsg", false)
                    resetState()
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Karte nicht gelöscht.") {
                dependencies.onFlowComplete("Kartenlöschung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun flowError(message: String) {
        dependencies.onFlowComplete("Interner Fehler im Lösch-Flow: $message", false)
        resetState()
    }

    fun isActive(): Boolean = currentState != State.IDLE

    fun resetState() {
        currentState = State.IDLE
        currentBoardId = null
        currentBoardName = null
        currentListId = null
        currentListName = null
        currentCardId = null
        currentCardName = null
        pendingBoardNameFromAI = null
        pendingListNameFromAI = null
        pendingCardNameFromAI = null
        dependencies.updateUiContext("")
    }

    private fun buildContextText(): String {
        val sb = StringBuilder("Aktion: Karte löschen")
        currentBoardName?.let { sb.append("\nBoard: $it") }
        currentListName?.let { sb.append("\nListe: $it") }
        currentCardName?.let { sb.append("\nKarte: $it") }

        val nextStep = when (currentState) {
            State.WAITING_FOR_BOARD_NAME -> "Board-Name?"
            State.CONFIRM_BOARD_NOT_FOUND -> "Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_LIST_NAME -> "Listen-Name?"
            State.CONFIRM_LIST_NOT_FOUND -> "Liste nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_CARD_NAME -> "Kartenname?"
            State.CONFIRM_CARD_NOT_FOUND -> "Karte nicht gefunden, erneut? (Ja/Nein)"
            State.CONFIRM_DELETION -> "Löschung bestätigen? (Ja/Nein)"
            State.IDLE -> ""
        }
        if (nextStep.isNotBlank()) sb.append("\nNächster Schritt: $nextStep")
        return sb.toString()
    }
}