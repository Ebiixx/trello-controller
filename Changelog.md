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

### Behoben

- Kleinere Fehler und Stabilitätsverbesserungen
