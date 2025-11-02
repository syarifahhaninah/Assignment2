@echo off
REM Smart Collections Manager - Windows Launcher Script

echo =====================================
echo Smart Collections Manager - Launcher
echo =====================================
echo.

REM Find Java
if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    where java >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set "JAVA=java"
    ) else (
        echo Error: Java not found!
        echo Please install Java 17 or later and set JAVA_HOME
        pause
        exit /b 1
    )
)

REM Check Java version
"%JAVA%" -version 2>&1 | findstr /I "version" > temp.txt
set /p JAVA_VERSION_LINE=<temp.txt
del temp.txt
echo Java Version: %JAVA_VERSION_LINE%

REM Get script directory
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

REM Check if JAR exists
if exist "target\smart-collections-manager-1.0.0-all.jar" (
    set "JAR_FILE=target\smart-collections-manager-1.0.0-all.jar"
    echo Running fat JAR (with all dependencies)...
) else if exist "target\smart-collections-manager-1.0.0.jar" (
    set "JAR_FILE=target\smart-collections-manager-1.0.0.jar"
    echo Running JAR with lib directory...
) else (
    echo JAR file not found. Building project...
    where mvn >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        call mvn clean package
        if exist "target\smart-collections-manager-1.0.0-all.jar" (
            set "JAR_FILE=target\smart-collections-manager-1.0.0-all.jar"
        ) else (
            echo Build failed!
            pause
            exit /b 1
        )
    ) else (
        echo Error: Maven not found!
        echo Please install Maven or build the project manually
        pause
        exit /b 1
    )
)

echo Starting application...
echo.

REM Run the application
"%JAVA%" -jar "%JAR_FILE%" %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited with error code: %ERRORLEVEL%
    pause
)

exit /b %ERRORLEVEL%
