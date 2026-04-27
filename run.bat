@echo off
setlocal

set JAR=target\sold-short-fat.jar

if not exist "%JAR%" (
    echo.
    echo -------------------------------------------------------
    echo  sold-short-fat.jar not found.
    echo  Building it now with Maven — this may take a minute...
    echo -------------------------------------------------------
    echo.
    call mvn clean package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo BUILD FAILED. Make sure Maven is installed: https://maven.apache.org
        pause
        exit /b 1
    )
)

echo Launching Sold Short...
java -jar "%JAR%"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Could not launch the app.
    echo Make sure Java 17 or newer is installed: https://adoptium.net
    pause
)
