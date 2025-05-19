# Changelog

## [1.2.1] - 2025-05-19

### Hinzugefügt

- **Karte archivieren Dialogfluss**: Neuer mehrstufiger Dialog zum Archivieren von Karten, analog zu den bestehenden Flows für "Karte erstellen", "Liste erstellen" und "Karte umbenennen".
  - Neuer Flow `ArchiveCardFlow` mit eigenem State-Handling, Fehlerbehandlung und Bestätigungsdialog.
  - Integration der API-Funktion `archiveCardOnTrello(key, token, cardId, onSuccess, onError)` zum Archivieren einer Karte (setzt `closed=true` via PUT-Request).
  - Nutzer können jetzt per Sprachbefehl Karten archivieren, inklusive Auswahl von Board, Liste und Karte sowie Bestätigung.
  - UI-Kontextanzeige und Statusmeldungen für den Archivierungsprozess.

## [1.2.0] - 2025-05-19

### Hinzugefügt

- **Karte umbenennen Dialogfluss**: Implementierung eines mehrstufigen Dialogs zum Umbenennen von Karten.
  - Neue `ControllerState` Enum-Werte (`RenameCard_WaitingForBoardName`, `RenameCard_WaitingForListName`, `RenameCard_WaitingForOldCardName`, `RenameCard_WaitingForNewCardName`, `RenameCard_ConfirmRenaming`). Nutzt `ArchiveCard_ConfirmCardNotFound` für die "alte Karte nicht gefunden"-Bestätigung.
  - Entsprechende Zustandsvariablen (`currentCardToRenameId`, `currentOldCardName`, `currentNewCardName`, `pendingOldCardNameFromAI`, `pendingNewCardNameFromAI`).
  - Erweiterung von `resetMultiTurnFlow` und der Logik in `WaitingForInitialCommand` zur Behandlung der "rename_card" Aktion.
  - Neue Hilfsfunktionen `proceedToAskListNameForRename`, `proceedToAskOldCardName`, `proceedToAskNewCardName`.
  - Handler für die neuen `ControllerState`-Werte implementiert.
  - UI-Statusanzeige für den Kontext "Karte umbenennen" angepasst.
- **API-Funktion für Kartenumbenennung**:
  - `renameCardOnTrello(key, token, cardId, newName, onSuccess, onError)` in `api.kt` hinzugefügt, um eine Karte umzubenennen (via PUT-Request).
- **Karte archivieren Dialogfluss**: Implementierung eines mehrstufigen Dialogs zum Archivieren von Karten, analog zu "Karte erstellen" und "Liste erstellen".
  - Neue `ControllerState` Enum-Werte für den Archivierungsablauf (`ArchiveCard_WaitingForBoardName`, `ArchiveCard_WaitingForListName`, `ArchiveCard_WaitingForCardName`, `ArchiveCard_ConfirmCardNotFound`, `ArchiveCard_ConfirmArchival`).
  - Entsprechende Zustandsvariablen (`currentCardToArchiveId`, `currentCardToArchiveName`, `pendingCardToArchiveNameFromAI`).
  - Erweiterung von `resetMultiTurnFlow` und der Logik in `WaitingForInitialCommand` zur Behandlung der "archive_card" Aktion.
  - Neue Hilfsfunktionen `proceedToAskListNameForArchive` und `proceedToAskCardToArchiveName`.
  - Handler für die neuen `ControllerState`-Werte implementiert, inklusive Bestätigungsdialoge und Fehlerbehandlung.
  - UI-Statusanzeige für den Kontext "Karte archivieren" angepasst.
- **API-Funktionen für Karten**:
  - `getAllCardsInList(key, token, listId, onResult, onError)` in `api.kt` hinzugefügt, um alle Karten (Name, ID) einer spezifischen Liste abzurufen. Verwendet `HttpURLConnection`.
  - `archiveCardOnTrello(key, token, cardId, onSuccess, onError)` in `api.kt` hinzugefügt, um eine Karte über ihre ID zu archivieren (setzt `closed=true` via PUT-Request). Verwendet `HttpURLConnection`.
- **Karten-Matching**:
  - `getBestMatchingCardIdInList` in `MainActivity.kt` hinzugefügt, um die am besten passende Karte basierend auf einem Suchbegriff in einer Liste zu finden.
- **Utility-Dateien**:
  - `utils/StringUtils.kt` für String-bezogene Hilfsfunktionen.
  - `utils/UiUtils.kt` für UI-bezogene Hilfsfunktionen.

### Geändert

- `buildUiConfirmationText` in `MainActivity.kt` erweitert, um Bestätigungstexte für die Aktionen "archive_card" und "rename_card" zu generieren. (Hinweis: Diese Funktion wurde später nach `UiUtils.kt` verschoben).
- Die Logik in `MainActivity.kt` (`when (state)`) wurde erheblich erweitert, um die neuen Dialogflüsse ("Karte archivieren", "Karte umbenennen") zu unterstützen.
- Die Zustandsbehandlung für `AddCard_ConfirmBoardNotFound`, `AddCard_ConfirmListNotFound` und `ArchiveCard_ConfirmCardNotFound` wurde generalisiert, um von mehreren Flüssen genutzt zu werden.
- Die UI-Anzeige (`currentActionInfo`) in `MainActivity.kt` wurde angepasst, um den Kontext der neuen Aktionen korrekt darzustellen.
- **Refactoring von Hilfsfunktionen**:
  - `normalizeSpeechCommand` aus `MainActivity.kt` nach `utils/StringUtils.kt` verschoben.
  - `buildUiConfirmationText` aus `MainActivity.kt` nach `utils/UiUtils.kt` verschoben.
  - `getBestMatchingBoardId` und `getBestMatchingListId` aus `MainActivity.kt` nach `api.kt` verschoben.
  - `MainActivity.kt` verwendet nun die ausgelagerten Funktionen.
- Refactored `MainScreen` composable into its own file (`app/src/main/java/com/example/trellocontroller/ui/MainScreen.kt`).
- Updated `MainActivity` to use the new `MainScreen` composable.
- Corrected package name in `Theme.kt` from `com.example.trellocontroller.ui` to `com.example.trellocontroller.ui.theme`.
- Adjusted `TrelloControllerTheme` import in `MainActivity.kt` to reflect the new package structure.
- Removed duplicate `Typography` definition from `Theme.kt` to resolve conflicting declarations, relying on `Type.kt` for typography styles.

## [1.1.0] - 2025-05-18

### Hinzugefügt

- **Mehrstufiger Dialog für "Liste erstellen"**:
  - Neue `ControllerState` Enum-Werte: `AddList_WaitingForBoardName`, `AddList_WaitingForNewListName`, `AddList_ConfirmCreation`.
  - Neue Zustandsvariablen: `currentNewListName`, `pendingNewListNameFromAI`.
  - Neue Hilfsfunktion `proceedToAskNewListName`.
  - Logik in `WaitingForInitialCommand` erweitert, um den mehrstufigen Dialog für "add_list" zu starten, wenn die KI die Aktion erkennt.
  - Behandlung der neuen Zustände im Haupt-`when`-Block.
- **Mehrstufiger Dialog für "Karte erstellen"**:
  - Umstellung von `ControllerState.WaitingForConfirmation` auf einen detaillierteren, mehrstufigen Dialogablauf.
  - Neue `ControllerState` Enum-Werte: `AddCard_WaitingForBoardName`, `AddCard_ConfirmBoardNotFound`, `AddCard_WaitingForListName`, `AddCard_ConfirmListNotFound`, `AddCard_WaitingForCardTitle`, `AddCard_ConfirmCreation`.
  - Neue Zustandsvariablen für den aktuellen (`current...`) und von der KI vorgeschlagenen (`pending...FromAI`) Board-, Listen- und Kartennamen.
  - `resetMultiTurnFlow()` zum Zurücksetzen dieser Zustandsvariablen.
  - Neue Hilfsfunktionen (`proceedToAskBoardName`, `proceedToAskListNameForCard`, `proceedToAskCardTitle`) zur Steuerung des Dialogs.
  - Logik in `WaitingForInitialCommand` angepasst, um bei Erkennung von "add_card" den mehrstufigen Dialog zu starten und ggf. von der KI erkannte Entitäten zu übernehmen.
  - Implementierung der Handler für die neuen `ControllerState`-Werte, inklusive Nutzerinteraktion (Spracheingabe, Bestätigung) und API-Aufrufen (`getBestMatchingBoardId`, `getBestMatchingListId`, `addCardToTrello`).
  - Fehlerbehandlung und optionale Anzeige von Vorschlägen, wenn Boards/Listen nicht gefunden werden.
- **API-Hilfsfunktionen**:
  - `getAllBoards(key, token, onResult, onError)` in `api.kt` zum Abrufen aller Boards (Name, ID).
  - `getAllLists(key, token, boardId, onResult, onError)` in `api.kt` zum Abrufen aller Listen (Name, ID) eines Boards.
- **Matching-Logik**:
  - `findBestMatch(name, candidates)`: Verbesserte Funktion zur Ermittlung des besten Treffers unter Berücksichtigung von Teilstrings und Levenshtein-Distanz mit dynamischem Schwellenwert.
  - `getBestMatchingBoardId(key, token, boardName, onResult, onError)`: Verwendet `getAllBoards` und `findBestMatch`.
  - `getBestMatchingListId(key, token, boardId, listName, onResult, onError)`: Verwendet `getAllLists` und `findBestMatch`.
- **UI-Verbesserungen**:
  - Anzeige des aktuellen `ControllerState` und des Kontexts der laufenden Aktion (z.B. welches Board/Liste gerade bearbeitet wird).
  - Klarere Trennung von erkannter Sprache, KI-Aktion/Kontext und Trello-Ergebnis.

### Geändert

- `askAzureOpenAI` in `api.kt`: Robustere JSON-Extraktion aus der KI-Antwort.
- `buildTrelloPrompt` in `api.kt`: Kann nun optional Listen von Boards und Listen pro Board als Kontext für die KI erhalten.
- `normalizeSpeechCommand` in `MainActivity.kt`: Weitere Ersetzungen hinzugefügt.
- `speakWithCallback` in `MainActivity.kt`: Stellt sicher, dass der `UtteranceProgressListener` nach Gebrauch entfernt wird, um mehrfache `onDone`-Aufrufe zu verhindern.
- Die Logik für `ControllerState.WaitingForConfirmation` wurde angepasst, um nur noch für Aktionen zu greifen, die keinen eigenen mehrstufigen Dialog haben.

## [1.0.0] - 2025-05-17

### Hinzugefügt

- Grundlegende App-Struktur mit Jetpack Compose.
- Spracherkennung über `RecognizerIntent`.
- Text-to-Speech-Funktionalität.
- Azure OpenAI Anbindung (`askAzureOpenAI`) zur Interpretation von Sprachbefehlen.
- Trello API Anbindung (`getBoardIdByName`, `getListIdByName`, `addCardToTrello`, `addListToBoard`, etc. in `api.kt`) mit OkHttp.
- `buildTrelloPrompt` zur Erstellung des Prompts für Azure OpenAI.
- `normalizeSpeechCommand` zur Vorverarbeitung der Spracheingabe.
- Einfacher Zustandsautomat (`ControllerState`) zur Steuerung des Ablaufs.
- UI zur Anzeige des erkannten Texts, der von der KI vorgeschlagenen Aktion und des Trello-Ergebnisses.
- Speicherung von API-Keys und Endpunkten in `local.properties` und Zugriff über `BuildConfig`.
- Levenshtein-Distanzfunktion zur Verbesserung der Namenserkennung.
- Grundlegende Fehlerbehandlung und Nutzerfeedback per Sprache.
- Initiales Changelog erstellt.

## [Unreleased]

### Fixed
- Korrekte Übergabe des `trelloResult`-Wertes (String) an die `MainScreen`-Komponente in `MainActivity.kt`, um einen Typenkonflikt zu beheben.
