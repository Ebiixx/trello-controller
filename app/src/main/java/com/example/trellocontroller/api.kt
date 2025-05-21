package com.example.trellocontroller

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

fun askAzureOpenAI(
    message: String,
    apiKey: String,
    endpoint: String,
    deployment: String,
    apiVersion: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    val url = "${endpoint}openai/deployments/$deployment/chat/completions?api-version=$apiVersion"
    val escaped = JSONObject.quote(message)
    val bodyJson = """
        {
          "messages": [{"role": "user", "content": $escaped}],
          "max_tokens": 1000,
          "temperature": 0.3
        }
    """.trimIndent()
    val body = bodyJson.toRequestBody("application/json".toMediaType())
    val request = Request.Builder()
        .url(url)
        .addHeader("api-key", apiKey)
        .post(body)
        .build()
    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "Verbindung fehlgeschlagen")
        }
        override fun onResponse(call: Call, response: Response) {
            val resBody = response.body?.string() ?: return onError("Leere Antwort")
            try {
                val json = JSONObject(resBody)
                if (json.has("error")) {
                    val msg = json.getJSONObject("error").optString("message", "Unbekannter API-Fehler")
                    return onError("OpenAI-Fehler: $msg")
                }
                var content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                if (content.startsWith("```json")) content = content.removePrefix("```json").trim()
                if (content.startsWith("```")) content = content.removePrefix("```").trim()
                if (content.endsWith("```")) content = content.removeSuffix("```").trim()
                val start = content.indexOf('{')
                val end = content.lastIndexOf('}')
                if (start != -1 && end != -1 && end > start) {
                    val possibleJson = content.substring(start, end + 1)
                    try {
                        JSONObject(possibleJson)
                        onResult(possibleJson)
                        return
                    } catch (_: Exception) {}
                }
                onResult(JSONObject(content).toString())
            } catch (e: Exception) {
                onError("Fehler beim Parsen: ${e.message}\nOpenAI-Antwort: $resBody")
            }
        }
    })
}

fun buildTrelloPrompt(
    userMessage: String,
    boards: List<String>? = null,
    listsPerBoard: Map<String, List<String>>? = null
): String {
    val boardsInfo = boards?.let { "Verfügbare Boards: ${it.joinToString(", ")}" } ?: ""
    val listsInfo = listsPerBoard?.entries?.joinToString("\n") { (board, lists) ->
        "- $board: ${lists.joinToString(", ")}"
    } ?: ""
    return """
        Du bist ein Trello-Sprachassistent.
        Deine Aufgabe ist es, den nachfolgenden Sprachbefehl zu analysieren und **ausschließlich** ein gültiges JSON-Objekt (ohne jeglichen Kommentar, ohne Erklärung, ohne Text, ohne Markdown, ohne ```json oder ``` davor oder danach) zurückzugeben.

        $boardsInfo
        $listsInfo

        JSON-Format (nur Felder die erkannt wurden, Rest leer lassen):

        {
          "action": "add_card | delete_card | move_card | archive_card | add_comment | add_due_date | remove_due_date | add_label | remove_label | assign_member | remove_member | rename_card | update_desc | add_list",
          "board": "Boardname",
          "list": "Listenname",
          "title": "Kartentitel",
          "desc": "Beschreibung",
          "comment": "Kommentartext",
          "label": "Bezeichnung",
          "member": "Mitgliedsname",
          "due": "YYYY-MM-DD",
          "target_list": "NeueListenname"
        }

        **WICHTIG:** Antworte **wirklich ausschließlich mit dem JSON-Objekt**. Es darf KEIN zusätzlicher Text enthalten sein, KEIN Kommentar, KEIN Markdown-Codeblock, KEIN Hinweis – nur das reine JSON-Objekt.

        Sprachbefehl: $userMessage
    """.trimIndent()
}

fun getBoardIdByName(key: String, token: String, boardName: String, onResult: (String?) -> Unit, onError: (String) -> Unit) {
    val url = "https://api.trello.com/1/members/me/boards?key=$key&token=$token"
    val request = Request.Builder().url(url).build()
    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string() ?: return onError("Keine Antwort")
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                if (b.getString("name").equals(boardName, true)) return onResult(b.getString("id"))
            }
            onResult(null)
        }
    })
}

fun getListIdByName(key: String, token: String, boardId: String, listName: String, onResult: (String?) -> Unit, onError: (String) -> Unit) {
    val url = "https://api.trello.com/1/boards/$boardId/lists?key=$key&token=$token"
    val request = Request.Builder().url(url).build()
    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string() ?: return onError("Keine Antwort")
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val l = arr.getJSONObject(i)
                if (l.getString("name").equals(listName, true)) return onResult(l.getString("id"))
            }
            onResult(null)
        }
    })
}

fun addCardToTrello(
    key: String,
    token: String,
    listId: String,
    name: String,
    desc: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards"
    val form = FormBody.Builder()
        .add("key", key)
        .add("token", token)
        .add("idList", listId)
        .add("name", name)
        .add("desc", desc)
        .build()
    val req = Request.Builder().url(url).post(form).build()
    OkHttpClient().newCall(req).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorMsg = response.body?.string()
                onError("Fehler: ${response.code}, Nachricht: $errorMsg")
            }
        }
    })
}

fun addListToBoard(
    key: String,
    token: String,
    boardId: String,
    listName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/lists"
    val form = FormBody.Builder()
        .add("key", key)
        .add("token", token)
        .add("idBoard", boardId)
        .add("name", listName)
        .build()
    val req = Request.Builder().url(url).post(form).build()
    OkHttpClient().newCall(req).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorMsg = response.body?.string()
                onError("Fehler: ${response.code}, Nachricht: $errorMsg")
            }
        }
    })
}

fun findCardIdByTitle(
    key: String,
    token: String,
    boardId: String,
    listId: String,
    cardTitle: String,
    onResult: (String?) -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/lists/$listId/cards?key=$key&token=$token"
    val request = Request.Builder().url(url).build()
    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string() ?: return onError("Keine Antwort")
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                if (c.getString("name").equals(cardTitle, true)) return onResult(c.getString("id"))
            }
            onResult(null)
        }
    })
}

// Labels: List all labels on a board (needed for add/remove_label)
fun getLabelIdByName(
    key: String,
    token: String,
    boardId: String,
    labelName: String,
    onResult: (String?) -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/boards/$boardId/labels?key=$key&token=$token"
    val request = Request.Builder().url(url).build()
    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string() ?: return onError("Keine Antwort")
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val l = arr.getJSONObject(i)
                if (l.getString("name").equals(labelName, true)) return onResult(l.getString("id"))
            }
            onResult(null)
        }
    })
}

// Add label to card
fun addLabelToCard(
    key: String,
    token: String,
    cardId: String,
    labelId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId/idLabels"
    val form = FormBody.Builder()
        .add("value", labelId)
        .add("key", key)
        .add("token", token)
        .build()
    val req = Request.Builder().url(url).post(form).build()
    OkHttpClient().newCall(req).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorMsg = response.body?.string()
                onError("Fehler: ${response.code}, Nachricht: $errorMsg")
            }
        }
    })
}

// Remove label from card
fun removeLabelFromCard(
    key: String,
    token: String,
    cardId: String,
    labelId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId/idLabels/$labelId?key=$key&token=$token"
    val req = Request.Builder().url(url).delete().build()
    OkHttpClient().newCall(req).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorMsg = response.body?.string()
                onError("Fehler: ${response.code}, Nachricht: $errorMsg")
            }
        }
    })
}

// Add comment to card
fun addCommentToCard(
    key: String,
    token: String,
    cardId: String,
    comment: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId/actions/comments"
    val form = FormBody.Builder()
        .add("text", comment)
        .add("key", key)
        .add("token", token)
        .build()
    val req = Request.Builder().url(url).post(form).build()
    OkHttpClient().newCall(req).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Fehler")
        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorMsg = response.body?.string()
                onError("Fehler: ${response.code}, Nachricht: $errorMsg")
            }
        }
    })
}


// Liefert alle Boards zurück: Liste von Pair(Name, ID)
fun getAllBoards(
    key: String,
    token: String,
    onResult: (List<Pair<String, String>>) -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/members/me/boards?key=$key&token=$token"
    val request = okhttp3.Request.Builder().url(url).build()
    okhttp3.OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) = onError(e.message ?: "Fehler beim Laden der Boards")
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            val body = response.body?.string() ?: return onError("Keine Antwort von Trello (Boards)")
            try {
                val arr = org.json.JSONArray(body)
                val boards = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    boards.add(obj.getString("name") to obj.getString("id"))
                }
                onResult(boards)
            } catch (e: Exception) {
                onError("Fehler beim Parsen der Boards: ${e.message}")
            }
        }
    })
}

// Liefert alle Listen eines Boards zurück: Liste von Pair(Name, ID)
fun getAllLists(
    key: String,
    token: String,
    boardId: String,
    onResult: (List<Pair<String, String>>) -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/boards/$boardId/lists?key=$key&token=$token"
    val request = okhttp3.Request.Builder().url(url).build()
    okhttp3.OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) = onError(e.message ?: "Fehler beim Laden der Listen")
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            val body = response.body?.string() ?: return onError("Keine Antwort von Trello (Listen)")
            try {
                val arr = org.json.JSONArray(body)
                val lists = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    lists.add(obj.getString("name") to obj.getString("id"))
                }
                onResult(lists)
            } catch (e: Exception) {
                onError("Fehler beim Parsen der Listen: ${e.message}")
            }
        }
    })
}

fun renameCardOnTrello(
    key: String,
    token: String,
    cardId: String,
    newName: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId"
    val formBody = FormBody.Builder()
        .add("key", key)
        .add("token", token)
        .add("name", newName)
        .build()
    val request = Request.Builder()
        .url(url)
        .put(formBody)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "API call failed")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorBody = response.body?.string()
                onError("Error ${response.code}: $errorBody")
            }
            response.close()
        }
    })
}

// Function to move a card to a new list (and potentially new board)
fun moveCardOnTrello(
    key: String,
    token: String,
    cardId: String,
    targetListId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId"
    val formBody = FormBody.Builder()
        .add("key", key)
        .add("token", token)
        .add("idList", targetListId) // targetListId implies the target board as well
        .build()
    val request = Request.Builder()
        .url(url)
        .put(formBody)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "API call failed to move card")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorBody = response.body?.string()
                onError("Error ${response.code} moving card: $errorBody")
            }
            response.close()
        }
    })
}

fun getCardsFromList(
    key: String,
    token: String,
    listId: String,
    onResult: (List<Pair<String, String>>) -> Unit, // List of (CardName, CardId)
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/lists/$listId/cards?key=$key&token=$token&fields=name,id"
    val request = Request.Builder().url(url).build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "API call failed")
        }

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                onError("Error ${response.code}: ${response.body?.string()}")
                response.close()
                return
            }
            val body = response.body?.string()
            if (body == null) {
                onError("Empty response body")
                response.close()
                return
            }
            try {
                val cardsArray = JSONArray(body)
                val resultList = mutableListOf<Pair<String, String>>()
                for (i in 0 until cardsArray.length()) {
                    val cardObject = cardsArray.getJSONObject(i)
                    resultList.add(Pair(cardObject.getString("name"), cardObject.getString("id")))
                }
                onResult(resultList)
            } catch (e: Exception) {
                onError("Error parsing cards: ${e.message}")
            }
            response.close()
        }
    })
}

fun getBestMatchingBoardId(
    apiKey: String,
    apiToken: String,
    boardNameInput: String,
    onResult: (boardId: String?, matchedBoardName: String?) -> Unit,
    onError: (String) -> Unit
) {
    getAllBoards(apiKey, apiToken,
        onResult = { boards ->
            val exactMatch = boards.firstOrNull { it.first.equals(boardNameInput, ignoreCase = true) }
            if (exactMatch != null) {
                onResult(exactMatch.second, exactMatch.first)
                return@getAllBoards
            }
            val partialMatch = boards.firstOrNull { it.first.contains(boardNameInput, ignoreCase = true) }
            if (partialMatch != null) {
                onResult(partialMatch.second, partialMatch.first)
            } else {
                onResult(null, null)
            }
        },
        onError = onError
    )
}

fun getBestMatchingListId(
    apiKey: String,
    apiToken: String,
    boardId: String,
    listNameInput: String,
    onResult: (listId: String?, matchedListName: String?) -> Unit,
    onError: (String) -> Unit
) {
    getAllLists(apiKey, apiToken, boardId,
        onResult = { lists ->
            val exactMatch = lists.firstOrNull { it.first.equals(listNameInput, ignoreCase = true) }
            if (exactMatch != null) {
                onResult(exactMatch.second, exactMatch.first)
                return@getAllLists
            }
            val partialMatch = lists.firstOrNull { it.first.contains(listNameInput, ignoreCase = true) }
            if (partialMatch != null) {
                onResult(partialMatch.second, partialMatch.first)
            } else {
                onResult(null, null)
            }
        },
        onError = onError
    )
}

fun archiveCardOnTrello(
    key: String,
    token: String,
    cardId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId"
    val formBody = FormBody.Builder()
        .add("key", key)
        .add("token", token)
        .add("closed", "true")
        .build()
    val request = Request.Builder()
        .url(url)
        .put(formBody)
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "API call failed")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorBody = response.body?.string()
                onError("Error ${response.code}: $errorBody")
            }
            response.close()
        }
    })
}

fun deleteCardOnTrello(
    key: String,
    token: String,
    cardId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val url = "https://api.trello.com/1/cards/$cardId?key=$key&token=$token"
    val request = Request.Builder()
        .url(url)
        .delete() // HTTP DELETE method
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e.message ?: "API call failed to delete card")
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                onSuccess()
            } else {
                val errorBody = response.body?.string()
                onError("Error ${response.code} deleting card: $errorBody")
            }
            response.close() // Ensure the response body is closed
        }
    })
}