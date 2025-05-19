package com.example.trellocontroller.flows

interface AddListFlowDependencies {
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

    fun addListToTrello(
        boardId: String,
        listName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )

    fun onFlowComplete(message: String, success: Boolean)
    fun updateUiContext(context: String)
}