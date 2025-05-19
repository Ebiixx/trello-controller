# Changelog

All notable changes to this project will be documented in this file.

## [v0.2.0] - 2025-05-19

### Hinzugefügt

- **Karte verschieben Funktionalität**: Implementierung eines neuen mehrstufigen Ablaufs zum Verschieben von Trello-Karten zwischen verschiedenen Listen und Boards mittels Sprachbefehlen. Dies umfasst:
  - Identifizierung von Quell-Board, Quell-Liste und der zu verschiebenden Karte.
  - Identifizierung von Ziel-Board (kann dasselbe wie die Quelle sein) und Ziel-Liste.
  - Bestätigungsschritte für jede Entität, falls nicht eindeutig von der KI oder dem ursprünglichen Befehl identifiziert.
  - API-Integration für `moveCardOnTrello`.
- Sprachansagen und UI-Kontextaktualisierungen während des gesamten "Karte verschieben"-Ablaufs.

### Verbesserungen

- **Dialogrobustheit in Flows**: Verbesserung der Benutzerinteraktion in allen mehrstufigen Abläufen (Karte hinzufügen, Liste hinzufügen, Karte umbenennen, Karte archivieren, Karte verschieben):
  - Wenn ein Board- oder Listenname nicht gefunden wird und der Benutzer ablehnt, Vorschläge aufzulisten (z. B. mit "Nein" antwortet), fordert der Flow den Benutzer nun auf, den Namen zu wiederholen, anstatt die Aktion sofort abzubrechen.
  - Wenn der wiederholte Name ebenfalls nicht gefunden wird, wird das Angebot, Vorschläge aufzulisten, erneut präsentiert.
- Die Fehlerbehandlung bei API-Aufrufen innerhalb der Flows fordert den Benutzer nun konsistent auf, die Eingabe erneut zu versuchen.

## [v0.1.0] - (Datum der vorherigen Version)

### Hinzugefügt

- Grundlegende Funktionen:
  - Karte hinzufügen
  - Liste hinzufügen
  - Karte umbenennen
  - Karte archivieren
  - Grundlegende Azure OpenAI-Integration
  - ... (weitere ursprüngliche Funktionen)
