package com.example.trellocontroller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    statusText: String,
    spokenText: String,
    contextualInfoText: String,
    trelloResultText: String,
    onStartSpeechOrResetClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Trello Sprachsteuerung",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = onStartSpeechOrResetClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
            ) {
                Text("Sprachbefehl starten / Reset", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
                    Text(
                        "Status: $statusText",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("🗣️ Erkannt:", style = MaterialTheme.typography.labelMedium)
                    Text(spokenText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))

                    if (contextualInfoText.isNotBlank()) {
                        Text("💬 Kontext:", style = MaterialTheme.typography.labelMedium)
                        Text(contextualInfoText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }

                    Text("📝 Trello-Status:", style = MaterialTheme.typography.labelMedium)
                    Text(trelloResultText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("© 2025 TrelloController", style = MaterialTheme.typography.bodySmall) // Jahr aktualisiert
    }
}