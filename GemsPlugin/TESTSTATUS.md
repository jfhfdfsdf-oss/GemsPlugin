# Teststatus – Gems

## Hier tatsächlich durchgeführt

- `pom.xml` wurde als XML geparst.
- `plugin.yml` und der GitHub-Actions-Workflow wurden als YAML geparst.
- Die Java-Datei wurde auf ausgeglichene geschweifte Klammern geprüft.
- Die acht Rezeptdefinitionen wurden auf gleich breite Zeilen geprüft.
- Die Projektdateien und die Plugin-Konfiguration wurden auf die Paper-26.3-/Java-25-Konfiguration abgeglichen.

## Noch nicht durchgeführt

- Maven-Compile gegen `paper-api:26.3.build.230-stable`.
- Start auf einem Paper-26.3-Server.
- Live-Crafting, Duplikations- und Mehrspielertests.
- Tests aller acht Gems auf den fünf Leveln.
- Tests mit FreeMCServer.

Grund: In dieser Arbeitsumgebung gibt es nur Java 21, kein Maven und keinen Zugriff auf das Paper-Maven-Repository. Der Build-Workflow in `.github/workflows/build.yml` ist dafür vorgesehen, wurde aber noch nicht ausgeführt.

**Daher gibt es in diesem Paket noch keine fertige oder nachweislich funktionierende `Gems.jar`.** Die JAR entsteht erst nach einem erfolgreichen Maven-Build.
