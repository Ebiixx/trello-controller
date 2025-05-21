package com.example.trellocontroller.flows

import com.example.trellocontroller.CommonFlowDependencies

interface RenameListFlowDependencies : CommonFlowDependencies {
    fun renameListOnTrello(listId: String, newName: String, onSuccess: () -> Unit, onError: (String) -> Unit)
}