// filepath: app/src/main/java/com/example/trellocontroller/flows/MoveCardFlowDependencies.kt
package com.example.trellocontroller.flows

interface MoveCardFlowDependencies {
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

    fun getCardsFromList( // To find the card to move
        listId: String,
        onResult: (List<Pair<String, String>>) -> Unit, // List of (CardName, CardId)
        onError: (String) -> Unit
    )

    fun moveCardOnTrello( // New API call
        cardId: String,
        targetListId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun onFlowComplete(message: String, success: Boolean)
    fun updateUiContext(context: String)
}