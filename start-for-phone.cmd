@echo off
rem Starts PCMania so the Android admin app on your phone can reach it.
rem Detects the PC's local network address, points BASE_URL at it (product photos
rem in the app are loaded from that address) and prints what to type in the app.
rem
rem Optional argument: a port number. Default 8070.  Example:  start-for-phone.cmd 8071
setlocal enabledelayedexpansion
title PCMania - server per telefon
cd /d "%~dp0"

set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

set "PORT=%~1"
if not defined PORT set "PORT=8070"

rem --- Java ---------------------------------------------------------------
if not exist "%JAVA_HOME%\bin\java.exe" (
  for %%J in ("C:\Users\%USERNAME%\.jdks\openjdk-25.0.1" "C:\Program Files\Java\jdk-26.0.2" "C:\Program Files\Java\latest") do (
    if not defined FOUNDJDK if exist "%%~J\bin\java.exe" set "FOUNDJDK=%%~J"
  )
  if defined FOUNDJDK (
    set "JAVA_HOME=!FOUNDJDK!"
  ) else (
    echo Nuk u gjet Java. Vendosni JAVA_HOME te nje JDK 21+.
    pause
    exit /b 1
  )
)
echo Java: %JAVA_HOME%

rem --- Is the port already taken? ------------------------------------------
rem Closing the black window does not always stop the server: mvnw starts Java as a
rem separate process that keeps holding the port. Starting again then fails with
rem "Port 8070 was already in use", which looks like the site is broken when in fact
rem it is still running from before.
call :portpid
if defined BUSYPID (
  echo.
  echo Porta %PORT% eshte e zene nga procesi !BUSYPID!.
  set "ALIVE="
  for /f "usebackq delims=" %%R in (`%PS% -NoProfile -Command "try { (Invoke-WebRequest -Uri 'http://localhost:%PORT%/' -UseBasicParsing -TimeoutSec 5).StatusCode } catch { 0 }"`) do set "ALIVE=%%R"
  if "!ALIVE!"=="200" (
    echo PCMania po punon tashme ne kete porte - nuk ka nevoje ta nisni perseri.
  ) else (
    echo Nuk duket si PCMania. Mund ta ndaloni ose te perdorni nje porte tjeter.
  )
  echo.
  echo   [r] Ndalo ate qe punon dhe nise nga e para
  echo   [h] Hap vetem faqen ne shfletues dhe dil
  echo   [n] Nuk dua asgje - dil
  echo.
  set /p ANS="Zgjidhni [r/h/n]: "
  if /i "!ANS!"=="h" (
    start "" "http://localhost:%PORT%"
    exit /b 0
  )
  if /i not "!ANS!"=="r" exit /b 0
  echo Duke ndaluar procesin !BUSYPID! ...
  taskkill /PID !BUSYPID! /T /F >nul 2>&1
  rem Windows keeps the socket for a moment after the process dies.
  timeout /t 3 >nul
  call :portpid
  if defined BUSYPID (
    echo Porta %PORT% eshte ende e zene. Provoni nje porte tjeter:  start-for-phone.cmd 8071
    pause
    exit /b 1
  )
)

rem --- Local network address ----------------------------------------------
set "LANIP="
for /f "usebackq delims=" %%I in (`%PS% -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Sort-Object @{ e = { $_.InterfaceAlias -notlike 'Wi-Fi*' } } | Select-Object -First 1 -ExpandProperty IPAddress"`) do set "LANIP=%%I"

if not defined LANIP (
  echo Nuk u gjet adresa IP e rrjetit. Kontrolloni lidhjen Wi-Fi.
  pause
  exit /b 1
)

rem --- Firewall ------------------------------------------------------------
netsh advfirewall firewall show rule name="PCMania %PORT%" >nul 2>&1
if errorlevel 1 (
  echo.
  echo Porta %PORT% nuk eshte e hapur ne firewall - telefoni nuk do te lidhet dot.
  set /p ANS="Ta shtoj rregullin tani? Do te kerkohet leje administratori [p/j]: "
  if /i "!ANS!"=="p" (
    rem remoteip=LocalSubnet: vetem pajisjet ne te njejtin rrjet, jo interneti.
    %PS% -NoProfile -Command "Start-Process netsh -Verb RunAs -ArgumentList 'advfirewall','firewall','add','rule','name=PCMania %PORT%','dir=in','action=allow','protocol=TCP','localport=%PORT%','remoteip=LocalSubnet'"
    timeout /t 3 >nul
  )
)

rem --- Run -----------------------------------------------------------------
set "BASE_URL=http://%LANIP%:%PORT%"

echo.
echo ==========================================================
echo   Faqja:   %BASE_URL%
echo   Admin:   %BASE_URL%/admin
echo.
echo   Ne aplikacionin e telefonit, fusha "Serveri":
echo       %LANIP%:%PORT%
echo ==========================================================
echo.

rem PORT is an environment variable here, which is exactly what application.yml reads
rem (server.port: ${PORT:8070}) - the same mechanism the hosted deployment uses.
call "%~dp0mvnw.cmd" spring-boot:run -Dspring-boot.run.profiles=dev
pause
exit /b %ERRORLEVEL%

rem --- helper: BUSYPID = pid listening on %PORT%, or undefined -------------
:portpid
set "BUSYPID="
for /f "usebackq delims=" %%I in (`%PS% -NoProfile -Command "Get-NetTCPConnection -State Listen -LocalPort %PORT% -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess"`) do set "BUSYPID=%%I"
exit /b 0
