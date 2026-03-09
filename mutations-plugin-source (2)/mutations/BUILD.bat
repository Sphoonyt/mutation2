@ECHO OFF
TITLE Mutations Plugin Builder
ECHO ==========================================
ECHO       Mutations Plugin - Build Tool
ECHO ==========================================
ECHO.

CD /D "%~dp0"

SET JAVA_HOME_LOCAL=%~dp0.build\java21
SET JAVA_EXE=%JAVA_HOME_LOCAL%\bin\java.exe
SET JAVA_URL=https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk

REM ---- Check for Java 21 ----
IF EXIST "%JAVA_EXE%" (
    ECHO [1/3] Java 21 found.
) ELSE (
    ECHO [1/3] Java 21 not found. Downloading now (this may take a minute^)...
    ECHO.
    MD "%JAVA_HOME_LOCAL%" 2>NUL
    SET JAVA_ZIP=%TEMP%\java21.zip
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%JAVA_URL%' -OutFile '%JAVA_ZIP%'}"
    IF ERRORLEVEL 1 (
        ECHO ERROR: Failed to download Java 21. Check your internet connection.
        PAUSE
        EXIT /B 1
    )
    ECHO Extracting Java 21...
    powershell -Command "Expand-Archive -Path '%JAVA_ZIP%' -DestinationPath '%~dp0.build\java21_tmp' -Force"
    DEL "%JAVA_ZIP%"
    REM Move the inner jdk folder up one level
    FOR /D %%i IN ("%~dp0.build\java21_tmp\*") DO (
        XCOPY "%%i\*" "%JAVA_HOME_LOCAL%\" /E /I /Q
    )
    RD /S /Q "%~dp0.build\java21_tmp"
    ECHO Java 21 ready!
    ECHO.
)

SET JAVA_HOME=%JAVA_HOME_LOCAL%
SET PATH=%JAVA_HOME_LOCAL%\bin;%PATH%

REM ---- Check for Maven ----
SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6
IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
    ECHO [2/3] Maven found.
) ELSE (
    ECHO [2/3] Maven not found. Downloading now...
    ECHO.
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
