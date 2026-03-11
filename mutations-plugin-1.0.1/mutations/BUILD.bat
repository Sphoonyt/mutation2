@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION
TITLE Mutations Plugin Builder
ECHO ==========================================
ECHO       Mutations Plugin - Build Tool
ECHO ==========================================
ECHO.

CD /D "%~dp0"

SET "JAVA_HOME_LOCAL=%~dp0.build\java21"
SET "JAVA_EXE=%~dp0.build\java21\bin\java.exe"
SET "JAVA_ZIP=%~dp0.build\java21.zip"
SET "JAVA_TMP=%~dp0.build\java21_tmp"
SET "JAVA_URL=https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5+11/OpenJDK21U-jdk_x64_windows_hotspot_21.0.5_11.zip"

MD "%~dp0.build" 2>NUL

REM ---- Check for Java 21 ----
IF EXIST "%JAVA_EXE%" (
    ECHO [1/3] Java 21 found.
    GOTO JAVA_DONE
)

ECHO [1/3] Downloading Java 21 (this may take a few minutes)...
ECHO.

powershell -NoProfile -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Write-Host 'Downloading...'; Invoke-WebRequest -Uri '!JAVA_URL!' -OutFile '!JAVA_ZIP!' -UseBasicParsing }"

IF NOT EXIST "!JAVA_ZIP!" (
    ECHO ERROR: Failed to download Java 21. Check your internet connection.
    PAUSE
    EXIT /B 1
)

ECHO Extracting Java 21...
powershell -NoProfile -Command "Expand-Archive -Path '!JAVA_ZIP!' -DestinationPath '!JAVA_TMP!' -Force"

IF ERRORLEVEL 1 (
    ECHO ERROR: Failed to extract Java 21.
    PAUSE
    EXIT /B 1
)

DEL "!JAVA_ZIP!"

FOR /D %%i IN ("!JAVA_TMP!\*") DO (
    XCOPY "%%i\*" "!JAVA_HOME_LOCAL!\" /E /I /Q >NUL
)
RD /S /Q "!JAVA_TMP!"

IF NOT EXIST "!JAVA_EXE!" (
    ECHO ERROR: Java extraction failed.
    PAUSE
    EXIT /B 1
)

ECHO Java 21 ready!
ECHO.

:JAVA_DONE
SET "JAVA_HOME=%JAVA_HOME_LOCAL%"
SET "PATH=%JAVA_HOME_LOCAL%\bin;%PATH%"

REM ---- Check for Maven ----
SET "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6"
IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
    ECHO [2/3] Maven found.
) ELSE (
    ECHO [2/3] Maven not found - will download automatically...
)

REM ---- Build ----
ECHO [3/3] Building plugin...
ECHO.
CALL mvnw.cmd clean package -q

IF ERRORLEVEL 1 (
    ECHO.
    ECHO ==========================================
    ECHO   BUILD FAILED - See errors above
    ECHO ==========================================
    PAUSE
    EXIT /B 1
)

ECHO.
ECHO ==========================================
ECHO   BUILD SUCCESSFUL!
ECHO ==========================================
ECHO.
ECHO Your plugin JAR is ready at:
ECHO   %~dp0target\Mutations-1.0.0.jar
ECHO.
ECHO Copy that file into your server's plugins\ folder
ECHO then restart your server.
ECHO.

IF EXIST "%~dp0target\Mutations-1.0.0.jar" (
    ECHO Opening the target folder now...
    explorer "%~dp0target"
)

PAUSE
ENDLOCAL
