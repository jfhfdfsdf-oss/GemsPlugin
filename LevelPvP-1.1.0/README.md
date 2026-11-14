# LevelPvP 1.1.0

Paper-Plugin-Grundprojekt für Minecraft 26.2, Java 25. Es enthält Level-/Herzverwaltung, Levelcaps, Level-Kristalle, Statistik- und Delivery-Menüs. Kampf-/Waffenmechaniken sind nicht Bestandteil dieses Grundprojekts.

## Build über GitHub Actions

1. Erstelle auf GitHub ein neues, leeres Repository.
2. Entpacke dieses Projekt und lade **alle Dateien und Ordner** in das Repository hoch.
3. Öffne im Repository den Tab **Actions**.
4. Starte `Build LevelPvP` über `Run workflow` oder löse den Workflow durch einen Push aus.
5. Nach erfolgreichem Build findest du unter dem Workflow-Lauf das Artifact `LevelPvP-1.1.0`.
6. Lade das Artifact herunter und kopiere die JAR in den `plugins`-Ordner deines Paper-Servers.

Die JAR liegt im Build unter `build/libs/LevelPvP-1.1.0.jar`.

## Lokaler Build

Benötigt JDK 25 und Gradle 9 oder neuer (der lokale Aufruf nutzt eine installierte Gradle-Version):

```bash
gradle clean build
```

## Befehle

- `/stats` – Statistikmenü
- `/delivery` – Items in die oberen Slots legen, Empfänger auswählen und Lieferungen abholen
- `/estractlevel <Anzahl>` – wandelt Level in Level-Kristalle um
- `/levelcap <Spieler> <1-50>` – individuelles Levelcap
- `/levelcap reset <Spieler>` – individuelles Cap entfernen
- `/levelcap global <1-50>` – globales Standard-Cap
- `/pvpmod setlevel <Spieler> <Level>` – Admin-Level setzen
- `/pvpmod addlevel <Spieler> <Anzahl>` – Admin-Level hinzufügen
- `/pvpmod removelevel <Spieler> <Anzahl>` – Admin-Level abziehen
- `/pvpmod stats <Spieler>` – Spielerdaten anzeigen
- `/pvpmod reset <Spieler>` – Spielerdaten zurücksetzen
- `/pvpmod reload` – Konfiguration neu laden

## Herzformel

- Level 0 = 10 Herzen.
- Level 0 bis 30: ein zusätzliches Herz je 3 Level.
- Level 30 bis 40: ein zusätzliches Herz je 2 Level.
- Level 40 bis 50: ein zusätzliches Herz je 2 Level.
- Level 50 = 30 Herzen.
- Negative Level: pro Level unter 0 ein Herz weniger, mindestens 1 Herz.

Die Herzberechnung verwendet ausschließlich ganze Herzen. Die maximale Gesundheit wird bei jedem Login und Levelwechsel neu gesetzt.

## Hinweis

Das Projekt baut gegen die Paper-26.2-API und benötigt Java 25. GitHub Actions installiert Java 25 automatisch. Der Build wurde in dieser Umgebung nicht ausgeführt, da hier kein JDK 25/Gradle installiert ist.

## Umfangshinweis

Dieses Grundprojekt implementiert keine Kampf- oder Waffenmechaniken und vergibt deshalb keine Level durch PvP-Kills. Die übrigen im README genannten Level-, Herz-, Delivery- und Verwaltungsfunktionen sind davon unabhängig.
