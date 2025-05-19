// filepath: app/src/main/java/com/example/trellocontroller/flows/MoveCardFlow.kt
package com.example.trellocontroller.flows

import org.json.JSONObject

class MoveCardFlow(private val dependencies: MoveCardFlowDependencies) {

    private enum class State {
        IDLE,
        WAITING_FOR_SOURCE_BOARD_NAME,
        CONFIRM_SOURCE_BOARD_NOT_FOUND,
        WAITING_FOR_SOURCE_LIST_NAME,
        CONFIRM_SOURCE_LIST_NOT_FOUND,
        WAITING_FOR_CARD_NAME,
        CONFIRM_CARD_NOT_FOUND,
        WAITING_FOR_TARGET_BOARD_NAME,
        CONFIRM_TARGET_BOARD_NOT_FOUND,
        WAITING_FOR_TARGET_LIST_NAME,
        CONFIRM_TARGET_LIST_NOT_FOUND,
        CONFIRM_MOVE
    }

    private var currentState: State = State.IDLE

    private var sourceBoardId: String? = null
    private var sourceBoardName: String? = null
    private var sourceListId: String? = null
    private var sourceListName: String? = null
    private var cardToMoveId: String? = null
    private var cardToMoveName: String? = null
    private var targetBoardId: String? = null
    private var targetBoardName: String? = null
    private var targetListId: String? = null
    private var targetListName: String? = null

    private var pendingSourceBoardNameFromAI: String? = null
    private var pendingSourceListNameFromAI: String? = null
    private var pendingCardNameFromAI: String? = null
    private var pendingTargetBoardNameFromAI: String? = null
    private var pendingTargetListNameFromAI: String? = null


    fun start(initialDataFromAI: JSONObject?) {
        resetState()
        currentState = State.WAITING_FOR_SOURCE_BOARD_NAME

        pendingSourceBoardNameFromAI = initialDataFromAI?.optString("board")?.takeIf { it.isNotBlank() }
        pendingSourceListNameFromAI = initialDataFromAI?.optString("list")?.takeIf { it.isNotBlank() }
        pendingCardNameFromAI = initialDataFromAI?.optString("title")?.takeIf { it.isNotBlank() }
        pendingTargetBoardNameFromAI = initialDataFromAI?.optString("target_board")?.takeIf { it.isNotBlank() }
        pendingTargetListNameFromAI = initialDataFromAI?.optString("target_list")?.takeIf { it.isNotBlank() }

        dependencies.updateUiContext(buildContextText())

        if (pendingSourceBoardNameFromAI != null) {
            handleSourceBoardNameInput(pendingSourceBoardNameFromAI!!)
        } else {
            dependencies.speakWithCallback("Von welchem Board möchtest du eine Karte verschieben?") {
                dependencies.startSpeechInput("Bitte nenne das Quell-Board")
            }
        }
    }

    fun handleSpokenInput(input: String) {
        dependencies.updateUiContext(buildContextText())
        when (currentState) {
            State.WAITING_FOR_SOURCE_BOARD_NAME -> handleSourceBoardNameInput(input)
            State.CONFIRM_SOURCE_BOARD_NOT_FOUND -> handleBoardNotFoundConfirmation(input, true)
            State.WAITING_FOR_SOURCE_LIST_NAME -> handleSourceListNameInput(input)
            State.CONFIRM_SOURCE_LIST_NOT_FOUND -> handleListNotFoundConfirmation(input, sourceBoardId, sourceBoardName, true)
            State.WAITING_FOR_CARD_NAME -> handleCardNameInput(input)
            State.CONFIRM_CARD_NOT_FOUND -> handleCardNotFoundConfirmation(input)
            State.WAITING_FOR_TARGET_BOARD_NAME -> handleTargetBoardNameInput(input)
            State.CONFIRM_TARGET_BOARD_NOT_FOUND -> handleBoardNotFoundConfirmation(input, false)
            State.WAITING_FOR_TARGET_LIST_NAME -> handleTargetListNameInput(input)
            State.CONFIRM_TARGET_LIST_NOT_FOUND -> handleListNotFoundConfirmation(input, targetBoardId, targetBoardName, false)
            State.CONFIRM_MOVE -> handleConfirmMove(input)
            State.IDLE -> { /* Should not happen */ }
        }
    }

    private fun handleSourceBoardNameInput(boardNameInput: String) {
        dependencies.getBestMatchingBoardId(boardNameInput,
            onResult = { boardId, matchedBoardName ->
                if (boardId != null) {
                    sourceBoardId = boardId
                    sourceBoardName = matchedBoardName
                    dependencies.updateUiContext(buildContextText())
                    proceedToAskSourceListName()
                } else {
                    dependencies.speakWithCallback("Das Quell-Board '$boardNameInput' wurde nicht gefunden. Soll ich dir mögliche Boards nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_SOURCE_BOARD_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Quell-Boardsuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne das Quell-Board")
                }
            }
        )
    }

    private fun proceedToAskSourceListName() {
        currentState = State.WAITING_FOR_SOURCE_LIST_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingSourceListNameFromAI != null) {
            handleSourceListNameInput(pendingSourceListNameFromAI!!)
            pendingSourceListNameFromAI = null
        } else {
            dependencies.speakWithCallback("Aus welcher Liste im Board '$sourceBoardName' soll die Karte verschoben werden?") {
                dependencies.startSpeechInput("Bitte nenne die Quell-Liste")
            }
        }
    }

    private fun handleSourceListNameInput(listNameInput: String) {
        val boardId = sourceBoardId ?: return flowError("Quell-Board ID fehlt.")
        dependencies.getBestMatchingListId(boardId, listNameInput,
            onResult = { listId, matchedListName ->
                if (listId != null) {
                    sourceListId = listId
                    sourceListName = matchedListName
                    dependencies.updateUiContext(buildContextText())
                    proceedToAskCardName()
                } else {
                    dependencies.speakWithCallback("Die Quell-Liste '$listNameInput' im Board '$sourceBoardName' wurde nicht gefunden. Soll ich dir mögliche Listen nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_SOURCE_LIST_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Quell-Listensuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne die Quell-Liste")
                }
            }
        )
    }

    private fun proceedToAskCardName() {
        currentState = State.WAITING_FOR_CARD_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingCardNameFromAI != null) {
            handleCardNameInput(pendingCardNameFromAI!!)
            pendingCardNameFromAI = null
        } else {
            dependencies.speakWithCallback("Wie heißt die Karte, die du verschieben möchtest?") {
                dependencies.startSpeechInput("Bitte nenne den Kartennamen")
            }
        }
    }

    private fun handleCardNameInput(cardNameInput: String) {
        val listId = sourceListId ?: return flowError("Quell-Listen ID fehlt.")
        dependencies.getCardsFromList(listId,
            onResult = { cards ->
                val matchingCards = cards.filter { it.first.equals(cardNameInput, ignoreCase = true) }
                when (matchingCards.size) {
                    1 -> {
                        cardToMoveId = matchingCards.first().second
                        cardToMoveName = matchingCards.first().first
                        dependencies.updateUiContext(buildContextText())
                        proceedToAskTargetBoardName()
                    }
                    0 -> {
                        dependencies.speakWithCallback("Ich konnte keine Karte mit dem Namen '$cardNameInput' in der Liste '$sourceListName' finden. Erneut versuchen?") {
                            dependencies.startSpeechInput("Ja oder Nein?")
                        }
                        currentState = State.CONFIRM_CARD_NOT_FOUND
                    }
                    else -> {
                        cardToMoveId = matchingCards.first().second
                        cardToMoveName = matchingCards.first().first
                        dependencies.speakWithCallback("Ich habe mehrere Karten namens '$cardNameInput' gefunden und wähle die erste: '${cardToMoveName}'. Fortfahren?") {
                             dependencies.startSpeechInput("Ja oder Nein?")
                        }
                        currentState = State.CONFIRM_CARD_NOT_FOUND 
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
        if (input.lowercase().contains("ja")) {
            if (cardToMoveId != null) { 
                 proceedToAskTargetBoardName()
            } else { 
                currentState = State.WAITING_FOR_CARD_NAME
                dependencies.speakWithCallback("Wie heißt die Karte?") {
                    dependencies.startSpeechInput("Bitte nenne den Kartennamen")
                }
            }
        } else {
            dependencies.speakWithCallback("Okay, Vorgang abgebrochen.") {
                dependencies.onFlowComplete("Kartenverschiebung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun proceedToAskTargetBoardName() {
        currentState = State.WAITING_FOR_TARGET_BOARD_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingTargetBoardNameFromAI != null) {
            handleTargetBoardNameInput(pendingTargetBoardNameFromAI!!)
            pendingTargetBoardNameFromAI = null
        } else {
            dependencies.speakWithCallback("Auf welches Board soll die Karte '$cardToMoveName' verschoben werden? Du kannst auch 'dasselbe' sagen.") {
                dependencies.startSpeechInput("Bitte nenne das Ziel-Board")
            }
        }
    }

    private fun handleTargetBoardNameInput(boardNameInput: String) {
        if (boardNameInput.equals("dasselbe", ignoreCase = true) || boardNameInput.equals("das selbe", ignoreCase = true)) {
            targetBoardId = sourceBoardId
            targetBoardName = sourceBoardName
            dependencies.updateUiContext(buildContextText())
            proceedToAskTargetListName()
            return
        }

        dependencies.getBestMatchingBoardId(boardNameInput,
            onResult = { boardId, matchedBoardName ->
                if (boardId != null) {
                    targetBoardId = boardId
                    targetBoardName = matchedBoardName
                    dependencies.updateUiContext(buildContextText())
                    proceedToAskTargetListName()
                } else {
                    dependencies.speakWithCallback("Das Ziel-Board '$boardNameInput' wurde nicht gefunden. Soll ich dir mögliche Boards nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_TARGET_BOARD_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Ziel-Boardsuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne das Ziel-Board")
                }
            }
        )
    }

    private fun proceedToAskTargetListName() {
        currentState = State.WAITING_FOR_TARGET_LIST_NAME
        dependencies.updateUiContext(buildContextText())
        if (pendingTargetListNameFromAI != null) {
            handleTargetListNameInput(pendingTargetListNameFromAI!!)
            pendingTargetListNameFromAI = null
        } else {
            dependencies.speakWithCallback("In welche Liste im Board '$targetBoardName' soll die Karte verschoben werden?") {
                dependencies.startSpeechInput("Bitte nenne die Ziel-Liste")
            }
        }
    }

    private fun handleTargetListNameInput(listNameInput: String) {
        val boardId = targetBoardId ?: return flowError("Ziel-Board ID fehlt.")
        dependencies.getBestMatchingListId(boardId, listNameInput,
            onResult = { listId, matchedListName ->
                if (listId != null) {
                    targetListId = listId
                    targetListName = matchedListName
                    dependencies.updateUiContext(buildContextText())
                    proceedToConfirmMove()
                } else {
                    dependencies.speakWithCallback("Die Ziel-Liste '$listNameInput' im Board '$targetBoardName' wurde nicht gefunden. Soll ich dir mögliche Listen nennen?") {
                        dependencies.startSpeechInput("Ja oder Nein?")
                    }
                    currentState = State.CONFIRM_TARGET_LIST_NOT_FOUND
                }
            },
            onError = { errorMsg ->
                dependencies.speakWithCallback("Fehler bei der Ziel-Listensuche: $errorMsg. Bitte erneut versuchen.") {
                    dependencies.startSpeechInput("Bitte nenne die Ziel-Liste")
                }
            }
        )
    }

    private fun handleBoardNotFoundConfirmation(input: String, isSourceBoard: Boolean) {
        if (input.lowercase().contains("ja")) {
            dependencies.getAllBoards(
                onResult = { boards ->
                    val boardNames = boards.map { it.first }
                    val text = if (boardNames.isNotEmpty()) {
                        "Mögliche Boards sind: ${boardNames.joinToString(", ", limit = 5)}. Welches Board meinst du?"
                    } else {
                        "Ich konnte keine Boards finden. Bitte nenne ein Board."
                    }
                    dependencies.speakWithCallback(text) {
                        dependencies.startSpeechInput(if (isSourceBoard) "Bitte nenne das Quell-Board" else "Bitte nenne das Ziel-Board")
                    }
                    currentState = if (isSourceBoard) State.WAITING_FOR_SOURCE_BOARD_NAME else State.WAITING_FOR_TARGET_BOARD_NAME
                },
                onError = {
                    dependencies.speakWithCallback("Fehler beim Laden der Boards. Bitte nenne ein Board.") {
                        dependencies.startSpeechInput(if (isSourceBoard) "Bitte nenne das Quell-Board" else "Bitte nenne das Ziel-Board")
                    }
                    currentState = if (isSourceBoard) State.WAITING_FOR_SOURCE_BOARD_NAME else State.WAITING_FOR_TARGET_BOARD_NAME
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Vorgang abgebrochen.") {
                dependencies.onFlowComplete("Kartenverschiebung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun handleListNotFoundConfirmation(input: String, boardIdForSuggestions: String?, boardNameForSuggestions: String?, isSourceList: Boolean) {
        val currentBoardId = boardIdForSuggestions ?: return flowError(if (isSourceList) "Quell-Board ID fehlt für Listenvorschläge." else "Ziel-Board ID fehlt für Listenvorschläge.")
        if (input.lowercase().contains("ja")) {
            dependencies.getAllLists(currentBoardId,
                onResult = { lists ->
                    val listNames = lists.map { it.first }
                    val text = if (listNames.isNotEmpty()) {
                        "Mögliche Listen im Board '$boardNameForSuggestions' sind: ${listNames.joinToString(", ", limit = 5)}. Welche Liste meinst du?"
                    } else {
                        "Ich konnte keine Listen im Board '$boardNameForSuggestions' finden. Bitte nenne eine Liste."
                    }
                    dependencies.speakWithCallback(text) {
                        dependencies.startSpeechInput(if (isSourceList) "Bitte nenne die Quell-Liste" else "Bitte nenne die Ziel-Liste")
                    }
                    currentState = if (isSourceList) State.WAITING_FOR_SOURCE_LIST_NAME else State.WAITING_FOR_TARGET_LIST_NAME
                },
                onError = {
                    dependencies.speakWithCallback("Fehler beim Laden der Listen. Bitte nenne eine Liste.") {
                         dependencies.startSpeechInput(if (isSourceList) "Bitte nenne die Quell-Liste" else "Bitte nenne die Ziel-Liste")
                    }
                     currentState = if (isSourceList) State.WAITING_FOR_SOURCE_LIST_NAME else State.WAITING_FOR_TARGET_LIST_NAME
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Vorgang abgebrochen.") {
                dependencies.onFlowComplete("Kartenverschiebung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun proceedToConfirmMove() {
        currentState = State.CONFIRM_MOVE
        dependencies.updateUiContext(buildContextText())
        val confirmMsg = "Soll die Karte '$cardToMoveName' von Liste '$sourceListName' (Board '$sourceBoardName') nach Liste '$targetListName' (Board '$targetBoardName') verschoben werden?"
        dependencies.speakWithCallback(confirmMsg) {
            dependencies.startSpeechInput("Ja oder Nein?")
        }
    }

    private fun handleConfirmMove(input: String) {
        if (input.lowercase().contains("ja")) {
            val cardId = cardToMoveId ?: return flowError("Karten ID fehlt.")
            val finalListId = targetListId ?: return flowError("Ziel-Listen ID fehlt.")

            dependencies.moveCardOnTrello(cardId, finalListId,
                onSuccess = {
                    val successMsg = "Karte '$cardToMoveName' wurde erfolgreich nach Liste '$targetListName' (Board '$targetBoardName') verschoben."
                    dependencies.onFlowComplete(successMsg, true)
                    resetState()
                },
                onError = { errMsg ->
                    dependencies.onFlowComplete("Fehler beim Verschieben der Karte: $errMsg", false)
                    resetState()
                }
            )
        } else {
            dependencies.speakWithCallback("Okay, Karte nicht verschoben.") {
                dependencies.onFlowComplete("Verschiebung abgebrochen.", false)
                resetState()
            }
        }
    }

    private fun flowError(message: String) {
        dependencies.onFlowComplete("Interner Fehler im Verschiebe-Flow: $message", false)
        resetState()
    }

    fun isActive(): Boolean = currentState != State.IDLE

    fun resetState() {
        currentState = State.IDLE
        sourceBoardId = null; sourceBoardName = null; sourceListId = null; sourceListName = null
        cardToMoveId = null; cardToMoveName = null
        targetBoardId = null; targetBoardName = null; targetListId = null; targetListName = null
        pendingSourceBoardNameFromAI = null; pendingSourceListNameFromAI = null; pendingCardNameFromAI = null
        pendingTargetBoardNameFromAI = null; pendingTargetListNameFromAI = null
        dependencies.updateUiContext("")
    }

    private fun buildContextText(): String {
        val sb = StringBuilder("Aktion: Karte verschieben")
        sourceBoardName?.let { sb.append("\nVon Board: $it") }
        sourceListName?.let { sb.append("\nVon Liste: $it") }
        cardToMoveName?.let { sb.append("\nKarte: $it") }
        targetBoardName?.let { sb.append("\nNach Board: $it") }
        targetListName?.let { sb.append("\nNach Liste: $it") }

        val nextStep = when (currentState) {
            State.WAITING_FOR_SOURCE_BOARD_NAME -> "Quell-Board?"
            State.CONFIRM_SOURCE_BOARD_NOT_FOUND -> "Quell-Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_SOURCE_LIST_NAME -> "Quell-Liste?"
            State.CONFIRM_SOURCE_LIST_NOT_FOUND -> "Quell-Liste nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_CARD_NAME -> "Kartenname?"
            State.CONFIRM_CARD_NOT_FOUND -> "Karte nicht gefunden/ausgewählt, fortfahren/erneut? (Ja/Nein)"
            State.WAITING_FOR_TARGET_BOARD_NAME -> "Ziel-Board? (oder 'dasselbe')"
            State.CONFIRM_TARGET_BOARD_NOT_FOUND -> "Ziel-Board nicht gefunden, Vorschläge? (Ja/Nein)"
            State.WAITING_FOR_TARGET_LIST_NAME -> "Ziel-Liste?"
            State.CONFIRM_TARGET_LIST_NOT_FOUND -> "Ziel-Liste nicht gefunden, Vorschläge? (Ja/Nein)"
            State.CONFIRM_MOVE -> "Verschiebung bestätigen? (Ja/Nein)"
            State.IDLE -> ""
        }
        if (nextStep.isNotBlank()) sb.append("\nNächster Schritt: $nextStep")
        return sb.toString()
    }
}