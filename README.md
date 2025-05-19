# TrelloController

**TrelloController** ist eine Android-App, mit der du deine Trello-Boards per Sprache steuern kannst.  
Die App nutzt moderne Sprach-zu-Text und KI-Technologie (Azure OpenAI), um Sprachbefehle zu verstehen und Trello-Operationen wie Karten anlegen, Listen verschieben oder Kommentare hinzufügen automatisch auszuführen.

---

## Features

- **Spracheingabe**: Steuere Trello komplett per Sprache (z. B. „Erstelle eine Karte in To Do im Board Jobagent“)

- **Fuzzy-Matching**: Toleriert kleine Versprecher, erkennt Boards/Listen auch bei ungenauer Aussprache

- **Text-to-Speech**: Die App bestätigt Aktionen oder fragt nach, bevor sie sie ausführt

- **Trello-Integration**: Fügt Karten hinzu, verschiebt sie, erstellt Listen etc.

- **KI-Parsing**: Nutzt Azure OpenAI (GPT) zur Interpretation deiner Sprachbefehle

- **Schickes UI**: Modernes Compose-UI für Übersicht und einfache Bedienung

---

## Getting Started

### Voraussetzungen

- Android Studio

- Ein eigenes [Trello API Key & Token](https://trello.com/app-key)

- Azure OpenAI API-Key & Endpoint (für GPT-Funktion)

### Setup

1.  **Repository klonen**

    ```bash
    git clone https://github.com/Ebiixx/trello-controller.git

    ```

2.  **Eigene Keys eintragen**  
    Lege eine Datei namens `gradle.properties` (im Projekt-Root und ggf. im `/app`-Verzeichnis) mit folgendem Inhalt an – **Werte ersetzen**:

    ```properties
    AZURE_OPENAI_API_KEY=dein_azure_key
    AZURE_OPENAI_ENDPOINT=https://...your-endpoint...
    AZURE_OPENAI_DEPLOYMENT=dein_deployment
    AZURE_OPENAI_API_VERSION=2023-05-15
    TRELLO_API_KEY=dein_trello_key
    TRELLO_API_TOKEN=dein_trello_token

    ```

    > **Wichtig:**  
    > Die Datei `gradle.properties` ist in `.gitignore` eingetragen, damit deine Keys niemals öffentlich werden!

3.  **Builden & Starten**  
    Öffne das Projekt in Android Studio und führe es auf deinem Gerät oder Emulator aus.

---

## Changelog

Eine detaillierte Liste der Änderungen für jede Version findest du in der [Changelog.md](Changelog.md).

---

## Sicherheitshinweis

**Lege deine Schlüssel niemals im Git-Repository ab!**  
Die wichtigsten privaten Daten werden durch `.gitignore` ausgeschlossen (siehe Projekt).

---

## Mitwirken

Pull Requests, Vorschläge und Verbesserungen sind willkommen!  
Erstelle ein Issue oder sende direkt einen PR.

---

## Lizenz

MIT License
