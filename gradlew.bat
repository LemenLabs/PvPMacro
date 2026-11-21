@echo off
setlocal
set "APP_HOME=%~dp0"
set "PROPS=%APP_HOME%gradle\wrapper\gradle-wrapper.properties"
for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPS%"') do set "DIST_URL=%%B"
for %%F in ("%DIST_URL%") do set "DIST_NAME=%%~nxF"
set "GRADLE_VERSION=%DIST_NAME:~-12,-8%"
rem Gradle distribution name is gradle-X.Y.Z-bin.zip; derive version by removing prefix and suffix.
set "GRADLE_VERSION=%DIST_NAME:gradle-=%"
set "GRADLE_VERSION=%GRADLE_VERSION:-bin.zip=%"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "INSTALL_DIR=%GRADLE_USER_HOME%\wrapper\manual-dists\%GRADLE_VERSION%"
set "GRADLE_BIN=%INSTALL_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='%DIST_URL%'; $z='%INSTALL_DIR%\%DIST_NAME%'; Invoke-WebRequest -Uri $u -OutFile $z; Expand-Archive -LiteralPath $z -DestinationPath '%INSTALL_DIR%' -Force; Remove-Item $z"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_BIN%" -p "%APP_HOME%" %*
exit /b %ERRORLEVEL%
