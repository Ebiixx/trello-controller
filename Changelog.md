# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden in dieser Datei dokumentiert.

Das Format basiert auf [Keep a Changelog](https://keepachangelog.com/de/1.0.0/),
und dieses Projekt hält sich an [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Hinzugefügt

- Erste Version des TrelloControllers
- Spracheingabe und -ausgabe
- Azure OpenAI Integration zur Befehlsinterpretation
- Grundlegende Trello-Aktionen (Karte hinzufügen, Liste hinzufügen)
- Fuzzy-Matching für Board- und Listennamen
- Verbesserte Normalisierung von Spracheingaben

### Geändert

- Dynamische Distanzanpassung für Fuzzy-Suche in `findBestMatch`
- Erweiterte Normalisierungsregeln in `normalizeSpeechCommand`
- Umstrukturierung des Dialogflusses für "Karte erstellen" zu einem mehrstufigen Prozess:
    - Separate Abfrage von Aktion, Board, Liste und Kartentitel.
    - Möglichkeit, Boards/Listen vorzuschlagen, wenn sie nicht gefunden wurden.
- Anpassung der `ControllerState` Enum und zugehöriger Logik für den neuen Dialogablauf.
- Erfolgsmeldungen im Trello-Status zeigen nun die durch Fuzzy-Matching korrigierten Board- und Listennamen an.
- Implementierung eines mehrstufigen Dialogflusses für "Liste erstellen":
    - Separate Abfrage von Board und neuem Listennamen.
    - Bestätigung vor der Erstellung.
- Implementierung eines mehrstufigen Dialogflusses für "Karte archivieren":
    - Abfrage von Board, Liste und Karte.
    - Bestätigung vor der Archivierung.
    - Vorschlag von Karten, falls nicht eindeutig.
- Implementierung eines mehrstufigen Dialogflusses für "Karte umbenennen":
    - Abfrage von Board, Liste, Karte und neuem Kartentitel.
    - Bestätigung vor der Umbenennung.
- Implementierung eines mehrstufigen Dialogflusses für "Karte verschieben":
    - Abfrage von Quell-Board, Quell-Liste, zu verschiebender Karte.
    - Abfrage von Ziel-Board und Ziel-Liste.
    - Bestätigung vor dem Verschieben.
- Einführung generischer "Nicht gefunden"-Bestätigungsdialoge für Boards, Listen und Karten mit der Option, Vorschläge anzuzeigen.
- Refactoring und Erweiterung der Zustandsvariablen (`current...`, `pending...FromAI`, `currentNew...`, `currentSource...`, `currentTarget...`) zur Unterstützung komplexerer Dialogabläufe.
- Überarbeitung und Erweiterung der `proceedToAsk...` Hilfsfunktionen für spezifischere Abfragen.
- Anpassung von `buildUiConfirmationText` und der UI-Statusanzeige zur Darstellung des Kontexts für alle neuen Aktionen.

### Behoben

- Kleinere Fehler und Stabilitätsverbesserungen
