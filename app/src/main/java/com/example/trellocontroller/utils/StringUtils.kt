package com.example.trellocontroller.utils

import java.util.Locale

fun normalizeSpeechCommand(command: String): String {
    return command.lowercase(Locale.GERMAN).trim()
}