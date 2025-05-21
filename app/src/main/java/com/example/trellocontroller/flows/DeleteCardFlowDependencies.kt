package com.example.trellocontroller.flows

interface DeleteCardFlowDependencies {
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

    fun getCardsFromList(
        listId: String,
        onResult: (List<Pair<String, String>>) -> Unit, // List of (CardName, CardId)
        onError: (String) -> Unit
    )

    fun deleteCardOnTrello(
        cardId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun onFlowComplete(message: String, success: Boolean)
    fun updateUiContext(context: String)
}