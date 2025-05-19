package com.example.trellocontroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import com.example.trellocontroller.flows.*
import com.example.trellocontroller.utils.buildUiConfirmationText
import com.example.trellocontroller.utils.normalizeSpeechCommand
import org.json.JSONObject

interface MainViewModelActions {
    fun speakWithCallback(text: String, onDone: (() -> Unit)? = null)
    fun startSpeechInput(prompt: String)
    // Potentially: fun runOnUiThread(action: () -> Unit) if needed by flows directly,
    // but better to handle via state updates.
}

// Common interface for Flow Dependencies to reduce boilerplate in ViewModel
interface CommonFlowDependencies {
    val trelloKey: String
    val trelloToken: String
    fun speakWithCallback(text: String, onDone: (() -> Unit)? = null)
    fun startSpeechInput(prompt: String)
    fun getBestMatchingBoardId(boardName: String, onResult: (boardId: String?, matchedBoardName: String?) -> Unit, onError: (String) -> Unit)
    fun getAllBoards(onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit)
    fun getBestMatchingListId(boardId: String, listName: String, onResult: (listId: String?, matchedListName: String?) -> Unit, onError: (String) -> Unit)
    fun getAllLists(boardId: String, onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit)
    fun onFlowComplete(message: String, success: Boolean)
    fun updateUiContext(context: String)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    var currentGlobalState by mutableStateOf(ControllerState.WaitingForInitialCommand)
        private set
    var spokenText by mutableStateOf("")
        private set
    var actionJsonForUi by mutableStateOf<JSONObject?>(null)
        private set
    private val _trelloResult = mutableStateOf("")
    val trelloResult: State<String> = _trelloResult
    var currentFlowContextText by mutableStateOf("")
        private set
    private var currentTrelloAction: String? = null

    // Weitere Zustände und Funktionen...

    fun setTrelloResult(message: String) {
        _trelloResult.value = message // Correctly setting the value
    }

    private val trelloKey: String = BuildConfig.TRELLO_API_KEY
    private val trelloToken: String = BuildConfig.TRELLO_API_TOKEN
    private val azureApiKey: String = BuildConfig.AZURE_OPENAI_API_KEY
    private val azureEndpoint: String = BuildConfig.AZURE_OPENAI_ENDPOINT
    private val azureDeploymentName: String = BuildConfig.AZURE_OPENAI_DEPLOYMENT
    private val azureApiVersion: String = BuildConfig.AZURE_OPENAI_API_VERSION

    lateinit var addCardFlow: AddCardFlow
    lateinit var addListFlow: AddListFlow
    lateinit var renameCardFlow: RenameCardFlow

    private var viewModelActions: MainViewModelActions? = null

    fun setViewModelActions(actions: MainViewModelActions) {
        this.viewModelActions = actions
        initializeFlows()
    }

    private fun initializeFlows() {
        val commonFlowDependenciesImpl = object : CommonFlowDependencies {
            override val trelloKey: String get() = this@MainViewModel.trelloKey
            override val trelloToken: String get() = this@MainViewModel.trelloToken

            override fun speakWithCallback(text: String, onDone: (() -> Unit)?) {
                viewModelActions?.speakWithCallback(text, onDone)
            }

            override fun startSpeechInput(prompt: String) {
                viewModelActions?.startSpeechInput(prompt)
            }

            override fun getBestMatchingBoardId(boardName: String, onResult: (boardId: String?, matchedBoardName: String?) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getBestMatchingBoardId(trelloKey, trelloToken, boardName, onResult, onError)
            }

            override fun getAllBoards(onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getAllBoards(trelloKey, trelloToken, onResult, onError)
            }

            override fun getBestMatchingListId(boardId: String, listName: String, onResult: (listId: String?, matchedListName: String?) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getBestMatchingListId(trelloKey, trelloToken, boardId, listName, onResult, onError)
            }

            override fun getAllLists(boardId: String, onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getAllLists(trelloKey, trelloToken, boardId, onResult, onError)
            }

            override fun onFlowComplete(message: String, success: Boolean) {
                _trelloResult.value = message // Use _trelloResult to set
                currentFlowContextText = ""
                viewModelActions?.speakWithCallback(message) { // Pass the message string directly
                    currentGlobalState = ControllerState.WaitingForInitialCommand
                }
            }

            override fun updateUiContext(context: String) {
                currentFlowContextText = context
            }
        }

        addCardFlow = AddCardFlow(object : AddCardFlowDependencies, CommonFlowDependencies by commonFlowDependenciesImpl {
            override fun addCardToTrello(listId: String, cardName: String, cardDesc: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.addCardToTrello(trelloKey, trelloToken, listId, cardName, cardDesc, onSuccess, onError)
            }
        })

        addListFlow = AddListFlow(object : AddListFlowDependencies, CommonFlowDependencies by commonFlowDependenciesImpl {
            override fun addListToTrello(boardId: String, listName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.addListToBoard(trelloKey, trelloToken, boardId, listName, onSuccess, onError)
            }
        })

        renameCardFlow = RenameCardFlow(object : RenameCardFlowDependencies, CommonFlowDependencies by commonFlowDependenciesImpl {
            override fun getCardsFromList(listId: String, onResult: (List<Pair<String, String>>) -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.getCardsFromList(trelloKey, trelloToken, listId, onResult, onError)
            }
            override fun renameCardOnTrello(cardId: String, newName: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
                com.example.trellocontroller.renameCardOnTrello(trelloKey, trelloToken, cardId, newName, onSuccess, onError)
            }
        })
    }

    fun handleSpeechRecognitionResult(spoken: String?) {
        val fixedSpoken = normalizeSpeechCommand(spoken ?: "")
        this.spokenText = fixedSpoken

        if (addCardFlow.isActive()) {
            addCardFlow.handleSpokenInput(fixedSpoken)
        } else if (::addListFlow.isInitialized && addListFlow.isActive()) {
            addListFlow.handleSpokenInput(fixedSpoken)
        } else if (::renameCardFlow.isInitialized && renameCardFlow.isActive()) {
            renameCardFlow.handleSpokenInput(fixedSpoken)
        } else {
            processGeneralSpokenInput(fixedSpoken)
        }
    }

    fun handleSpeechRecognitionError() {
        val activeFlow = addCardFlow.isActive() ||
                         (::addListFlow.isInitialized && addListFlow.isActive()) ||
                         (::renameCardFlow.isInitialized && renameCardFlow.isActive())

        if (activeFlow) {
            viewModelActions?.speakWithCallback("Spracheingabe fehlgeschlagen. Bitte erneut versuchen oder 'Abbrechen' sagen.") {
                // Flow could repeat its last question here if needed
            }
        } else {
            viewModelActions?.speakWithCallback("Spracheingabe fehlgeschlagen.") {
                if (currentGlobalState != ControllerState.WaitingForInitialCommand) {
                    currentGlobalState = ControllerState.WaitingForInitialCommand
                }
            }
        }
    }

    private fun processGeneralSpokenInput(input: String) {
        when (currentGlobalState) {
            ControllerState.WaitingForInitialCommand -> {
                actionJsonForUi = null
                resetMultiTurnStateVariables()
                val prompt = buildTrelloPrompt(input)
                askAzureOpenAI(
                    prompt, azureApiKey, azureEndpoint, azureDeploymentName, azureApiVersion,
                    onResult = { aiResultString ->
                        try {
                            val obj = JSONObject(aiResultString)
                            val action = obj.optString("action")
                            currentTrelloAction = action

                            if (action == "add_card") {
                                currentGlobalState = ControllerState.ExecutingAction
                                addCardFlow.start(obj)
                            } else if (action == "add_list") {
                                currentGlobalState = ControllerState.ExecutingAction
                                addListFlow.start(obj)
                            } else if (action == "rename_card") {
                                currentGlobalState = ControllerState.ExecutingAction
                                renameCardFlow.start(obj)
                            } else {
                                actionJsonForUi = obj
                                if (action.isNotBlank()) {
                                    val confirmationText = buildUiConfirmationText(obj)
                                    viewModelActions?.speakWithCallback(confirmationText) {
                                        viewModelActions?.startSpeechInput("Bitte mit Ja oder Nein antworten")
                                    }
                                    currentGlobalState = ControllerState.WaitingForConfirmation
                                } else {
                                    viewModelActions?.speakWithCallback("Ich habe die Aktion nicht verstanden.") {
                                        viewModelActions?.startSpeechInput("Was möchtest du tun?")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            setTrelloResult("Fehler beim Verarbeiten der KI-Antwort: ${e.message}")
                            viewModelActions?.speakWithCallback(trelloResult.value) {
                                currentGlobalState = ControllerState.WaitingForInitialCommand
                            }
                        }
                    },
                    onError = { errorMsg ->
                        setTrelloResult("Fehler bei der Anfrage an die KI: $errorMsg") // Use the setter
                        viewModelActions?.speakWithCallback(_trelloResult.value) { // Read from _trelloResult.value or trelloResult.value
                            currentGlobalState = ControllerState.WaitingForInitialCommand
                        }
                    }
                )
            }
            ControllerState.WaitingForConfirmation -> handleSingleShotConfirmation(input)
            ControllerState.ExecutingAction -> { /* Warten bis Flow fertig oder anderer Zustand gesetzt wird */ }
            ControllerState.Finished -> {
                resetAllStates()
            }
        }
    }

    fun handleSingleShotConfirmation(confirmation: String) {
        val normalizedConfirmation = confirmation.lowercase()
        if (normalizedConfirmation.contains("ja")) {
            actionJsonForUi?.let { json ->
                val action = json.optString("action")
                // TODO: Implement actual single-shot action execution here based on 'action' and 'json'
                setTrelloResult("Aktion '$action' wird ausgeführt (Dummy für Single-Shot)...")
                viewModelActions?.speakWithCallback("Aktion wird ausgeführt.") {
                    resetAllStates()
                }
            } ?: run {
                viewModelActions?.speakWithCallback("Keine Aktion zum Bestätigen vorhanden.") { resetAllStates() }
            }
        } else {
            viewModelActions?.speakWithCallback("Okay, Aktion abgebrochen.") { resetAllStates() }
        }
    }

    fun resetAllStates() {
        if (::addCardFlow.isInitialized && addCardFlow.isActive()) addCardFlow.resetState()
        if (::addListFlow.isInitialized && addListFlow.isActive()) addListFlow.resetState()
        if (::renameCardFlow.isInitialized && renameCardFlow.isActive()) renameCardFlow.resetState()
        resetMultiTurnStateVariables()
        spokenText = ""
        setTrelloResult("")
        actionJsonForUi = null
        currentFlowContextText = ""
        currentGlobalState = ControllerState.WaitingForInitialCommand
    }

    private fun resetMultiTurnStateVariables() {
        currentTrelloAction = null
    }

    fun onStartSpeechOrReset() {
        // TTS stop is handled in Activity
        resetAllStates()
        viewModelActions?.startSpeechInput("Was möchtest du tun?")
    }
}