@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "BASE_DIR=%~dp0") ELSE (SET "BASE_DIR=%__MVNW_ARG0_NAME__%")
@SET MAVEN_PROJECTBASEDIR=%BASE_DIR%

@SET WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties

@SET DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
@SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.6

IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO RUN_MAVEN

ECHO Downloading Maven 3.9.6 (first time only, please wait...)
ECHO.

@SET TEMP_ZIP=%TEMP%\apache-maven-3.9.6-bin.zip

powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%TEMP_ZIP%'}"

IF ERRORLEVEL 1 (
    ECHO ERROR: Failed to download Maven. Check your internet connection.
    PAUSE
    EXIT /B 1
)

ECHO Extracting Maven...
powershell -Command "Expand-Archive -Path '%TEMP_ZIP%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force"
DEL "%TEMP_ZIP%"

ECHO Maven downloaded successfully!
ECHO.

:RUN_MAVEN
IF DEFINED JAVA_HOME (
    SET "PATH=%JAVA_HOME%\bin;%PATH%"
)
"%MAVEN_HOME%\bin\mvn.cmd" %*
