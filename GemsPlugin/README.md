# Gems – Paper 26.3

Ein serverseitiges Utility-/Abenteuer-Plugin mit acht besonderen Gems, Rezepten, fünf Leveln, XP, passiven Effekten, aktiven Fähigkeiten und dauerhafter Speicherung.

## Enthaltene Gems

| Gem | Funktion |
|---|---|
| Speed | Aktive und passive Geschwindigkeitsstufen |
| Heil | Aktive und passive Regeneration |
| Wächter | Aktive und passive Resistenz |
| Herz | Erhöht die maximale Gesundheit und setzt sie beim Entfernen zurück |
| Feuer | Permanente Feuerresistenz |
| Ender | Sicherer Teleport in Blickrichtung mit Level-Reichweite |
| Wind | Kein Fallschaden und Windstoß nach oben |
| Phönix | Einmalige Rettung vor tödlichem Schaden mit Cooldown |

Diese Fassung ist bewusst auf Utility-, Bewegungs- und Schutzfähigkeiten beschränkt. Die Stärke-, Mace-, Speer-, Frost-, Vampir-, Donner- und Jäger-Angriffs-/Waffenmechaniken aus dem ursprünglichen Auftrag sind nicht enthalten. Es gibt deshalb acht statt fünfzehn Gems. In drei der verbleibenden Rezepte wurden die offensiven Zutaten aus der Vorlage durch neutrale Materialien ersetzt (Wächter: Diamant statt Schild; Wind: Phantomhaut statt Windladung; Phönix: Amethystscherbe statt Feuerladung).

## Technische Anforderungen

- Minecraft Java 26.3
- Paper 26.3
- Java 25 oder neuer auf dem Server
- JDK 25 und Maven nur zum Bauen der JAR

Paper 26.3 verlangt Java 25 oder neuer. Die verwendete Paper-API ist `26.3.build.230-stable`.

## JAR bauen

Mit JDK 25 und Maven im Projektordner ausführen:

```bash
mvn clean package
```

Die fertige JAR liegt danach unter:

```text
target/Gems.jar
```

### Kostenloser Build über GitHub Actions

Das Projekt enthält bereits `.github/workflows/build.yml`.

1. Erstelle ein kostenloses GitHub-Konto, falls du noch keines hast.
2. Erstelle ein neues öffentliches Repository.
3. Lade alle Dateien aus diesem Projekt in das Repository hoch, einschließlich des Ordners `.github`.
4. Öffne im Repository den Bereich **Actions**.
5. Wähle **Build Gems plugin** und starte **Run workflow**. Alternativ startet der Build automatisch nach einem Push auf `main`.
6. Öffne den fertigen Workflow-Lauf und lade unter **Artifacts** die Datei **Gems-Plugin** herunter.
7. Entpacke dieses Artifact. Darin liegt `Gems.jar`.

Der Workflow baut mit Java 25 und kompiliert gegen Paper 26.3. Er ist ein Build-Schritt, kein automatischer Test auf einem laufenden Minecraft-Server.

## Installation auf FreeMCServer

1. Stelle im FreeMCServer-Panel den Servertyp auf **Paper 26.3** (nicht Vanilla).
2. Prüfe, dass der Hoster Java 25 für den Server bereitstellt.
3. Stoppe den Server vollständig.
4. Öffne den Datei-Manager und den Server-Hauptordner.
5. Öffne den Ordner `plugins`. Falls er nicht angezeigt wird, starte Paper einmal und stoppe es anschließend wieder. Paper legt den Ordner normalerweise beim ersten Start an. Falls dein Panel ihn weiterhin nicht zeigt, prüfe den Datei-Manager des Hosters oder frage dessen Support.
6. Lade `Gems.jar` direkt in `plugins` hoch. Nicht entpacken.
7. Starte den Server neu.
8. Prüfe die Konsole auf `Gems enabled. Registered 8 utility Gems.`
9. Gib im Spiel `/gems` ein.

Falls FreeMCServer keine Java-25-Laufzeit oder kein Paper 26.3 anbietet, kann diese Plugin-Version dort nicht geladen werden. Dann muss zuerst die Serverversion bzw. Laufzeit im Panel angepasst werden.

## Befehle

Für alle Spieler:

- `/gems` – Übersicht, Level und XP
- `/gems recipe <gem>` – Rezeptmuster und Zutaten

Für Operatoren mit `gems.admin`:

- `/gems give <gem> [spieler]` – Gem vergeben
- `/gems reset <gem>` – globale einmalige Herstellungssperre zurücksetzen; vorhandene Items und Spielerfortschritte bleiben erhalten
- `/gems setlevel <gem> <1-5>` – Level des ausführenden Admin-Spielers setzen

Gem-IDs: `speed`, `heal`, `guardian`, `heart`, `fire`, `ender`, `wind`, `phoenix`.

## Crafting, XP und Speicherung

- Jedes enthaltene Gem kann serverweit nur einmal regulär hergestellt werden.
- Shift-Klick-Crafting eines Gems wird blockiert.
- Der UUID-basierte Erstbesitzer wird gespeichert.
- Jedes Gem im Inventar erhält 5 XP pro Minute, solange sein Besitzer online ist.
- Die vier Level-Schwellen sind 50, 200, 450 und 800 XP.
- Spielerfortschritt und Herstellungsstatus liegen in `plugins/Gems/data.yml`.
- Speicherung erfolgt alle fünf Minuten, nach Level-Ups, beim Craften, beim Verlassen und beim Server-Shutdown.
- Aktive Cooldowns werden beim Neustart zurückgesetzt.
- Gem-Items werden per PersistentDataContainer identifiziert, besitzen eine individuelle UUID und sind auf Stapelgröße 1 begrenzt.
- Passive Effekte prüfen Inventar, Hotbar, Offhand und Rüstung.
- Enderperlen-Teleport und Vanilla-Totem-Rettung sind deaktiviert.

## Teststatus / Grenzen

In der Arbeitsumgebung, in der diese Dateien erstellt wurden, stehen nur Java 21, kein Maven und kein ausgehender Zugriff auf das Paper-Maven-Repository zur Verfügung. **Die JAR konnte daher hier nicht kompiliert und nicht auf einem echten Paper-26.3-Server gestartet werden.** Der beigefügte GitHub-Workflow soll den Build mit Java 25 ausführen. Erst wenn dieser Workflow erfolgreich durchläuft, ist `Gems.jar` tatsächlich gebaut.

Nicht durch einen Serverlauf geprüft:

- tatsächliches Crafting und die Einmal-Sperre im Mehrspielerbetrieb
- alle passiven Effekte in Inventar, Rüstung und Offhand
- Teleport-Zielprüfung in unterschiedlichen Welten/Blöcken
- Verhalten von Heart- und Phönix-Gem mit anderen Plugins
- Neustart- und Mehrspieler-Verhalten

Die Phönix-Funktion schließt Void- und `/kill`-Schaden aus. Nicht jede denkbare selbstverschuldete Todesursache lässt sich mit der normalen DamageCause-Information eindeutig erkennen.

Bitte vor dem ersten Einsatz auf dem Hauptserver ein Backup der Welt anlegen und das Plugin zuerst auf einer Testwelt ausprobieren.
