package com.example.trellocontroller.utils

import org.json.JSONObject

fun buildUiConfirmationText(json: JSONObject): String {
    val action = json.optString("action", "Unbekannte Aktion")
    val board = json.optString("board", "")
    val list = json.optString("list", "")
    val title = json.optString("title", "")
    // val newTitle = json.optString("new_title", "") // If AI provides it for rename_card
    return when (action) {
        "add_card" -> "Soll Karte '$title' zu Liste '$list' auf Board '$board' hinzugefügt werden?"
        "add_list" -> "Soll Liste '$list' zu Board '$board' hinzugefügt werden?"
        "rename_card" -> "Soll Karte '$title' umbenannt werden? (Details folgen im Dialog)"
        else -> "Aktion: $action, Details: ${json.toString(2)}"
    }
}