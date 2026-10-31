@echo off
setlocal
java -version
if errorlevel 1 (
  echo Java was not found. Install JDK 25 first.
  pause
  exit /b 1
)
mvn -B clean package
if errorlevel 1 (
  echo Build failed. Check Java 25 and Maven.
  pause
  exit /b 1
)
echo.
echo Build successful: target\Gems.jar
pause
