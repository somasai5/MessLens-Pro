@echo off
REM Uses Maven from PATH if available; otherwise falls back to MAVEN_HOME / M2_HOME.
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
    mvn %*
) else if defined MAVEN_HOME (
    "%MAVEN_HOME%\bin\mvn.cmd" %*
) else if defined M2_HOME (
    "%M2_HOME%\bin\mvn.cmd" %*
) else (
    echo Maven not found. Install Apache Maven 3.8+ and add it to PATH, or set MAVEN_HOME.
    exit /b 1
)