@echo off
:: ─────────────────────────────────────────────────────────────────────────────
:: Sold Short — End-to-End Connection Test (Windows)
:: Requires: curl (built into Windows 10+) and Python 3
::
:: Usage:  test_connection.bat [SERVER_URL]
:: Default: http://localhost:8080
:: ─────────────────────────────────────────────────────────────────────────────

set SERVER=%~1
if "%SERVER%"=="" set SERVER=http://localhost:8080

set PASS=0
set FAIL=0
set TS=%TIME: =0%
set TS=%TS::=%
set TS=%TS:.=%

echo.
echo ===================================================
echo   Sold Short -- Connection Test --^> %SERVER%
echo ===================================================
echo.

:: ── 1. Health check ──────────────────────────────────────────────────────────
echo [1/9] Health check -- GET /api/market/stocks
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" "%SERVER%/api/market/stocks" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" (
    for /f %%i in ('python -c "import json; d=json.load(open(r'%TEMP%\ss_resp.json')); print(len(d))"') do set COUNT=%%i
    echo   [OK] Server reachable -- !COUNT! tickers in stock pool
    set /a PASS+=1
) else (
    echo   [FAIL] Server unreachable (HTTP %CODE%) -- is it running?
    echo.
    echo   Start the server with:
    echo     cd server ^&^& mvn spring-boot:run
    goto :summary
)

:: ── 2. Register Player 1 ─────────────────────────────────────────────────────
echo [2/9] Register host
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"username\":\"host_%TS%\",\"password\":\"pass123\",\"email\":\"\"}" "%SERVER%/api/auth/register" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" (
    for /f %%i in ('python -c "import json; print(json.load(open(r'%TEMP%\ss_resp.json'))['id'])"') do set P1_ID=%%i
    echo   [OK] Player 1 registered: host_%TS% (id=!P1_ID!)
    set /a PASS+=1
) else (
    echo   [FAIL] Player 1 registration failed (HTTP %CODE%)
    set /a FAIL+=1
)

:: ── 3. Register Player 2 ─────────────────────────────────────────────────────
echo [3/9] Register player
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"username\":\"player_%TS%\",\"password\":\"pass123\",\"email\":\"\"}" "%SERVER%/api/auth/register" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" (
    for /f %%i in ('python -c "import json; print(json.load(open(r'%TEMP%\ss_resp.json'))['id'])"') do set P2_ID=%%i
    echo   [OK] Player 2 registered: player_%TS% (id=!P2_ID!)
    set /a PASS+=1
) else (
    echo   [FAIL] Player 2 registration failed (HTTP %CODE%)
    set /a FAIL+=1
)

:: ── 4. Login both ────────────────────────────────────────────────────────────
echo [4/9] Login both players
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"username\":\"host_%TS%\",\"password\":\"pass123\"}" "%SERVER%/api/auth/login" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" ( echo   [OK] Player 1 login OK & set /a PASS+=1 ) else ( echo   [FAIL] Player 1 login failed & set /a FAIL+=1 )

curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"username\":\"player_%TS%\",\"password\":\"pass123\"}" "%SERVER%/api/auth/login" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" ( echo   [OK] Player 2 login OK & set /a PASS+=1 ) else ( echo   [FAIL] Player 2 login failed & set /a FAIL+=1 )

:: ── 5. Create league ─────────────────────────────────────────────────────────
echo [5/9] Create league
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"hostId\":!P1_ID!,\"name\":\"Test League %TS%\",\"maxPlayers\":4,\"startingLives\":3}" "%SERVER%/api/leagues" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" (
    for /f %%i in ('python -c "import json; print(json.load(open(r'%TEMP%\ss_resp.json'))['id'])"') do set LEAGUE_ID=%%i
    for /f %%i in ('python -c "import json; print(json.load(open(r'%TEMP%\ss_resp.json'))['joinCode'])"') do set JOIN_CODE=%%i
    echo   [OK] League created (id=!LEAGUE_ID!, joinCode=!JOIN_CODE!)
    set /a PASS+=1
) else (
    echo   [FAIL] Create league failed (HTTP %CODE%)
    set /a FAIL+=1
)

:: ── 6. Player 2 joins ────────────────────────────────────────────────────────
echo [6/9] Player 2 joins league
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"userId\":!P2_ID!,\"joinCode\":\"!JOIN_CODE!\"}" "%SERVER%/api/leagues/0/join" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" ( echo   [OK] Player 2 joined league & set /a PASS+=1 ) else ( echo   [FAIL] Join league failed (HTTP %CODE%) & set /a FAIL+=1 )

:: ── 7. Start draft ───────────────────────────────────────────────────────────
echo [7/9] Start draft
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"userId\":!P1_ID!}" "%SERVER%/api/leagues/!LEAGUE_ID!/draft/start" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" ( echo   [OK] Draft started & set /a PASS+=1 ) else ( echo   [FAIL] Start draft failed (HTTP %CODE%) & set /a FAIL+=1 )

:: ── 8. Submit picks ──────────────────────────────────────────────────────────
echo [8/9] Submit picks
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"userId\":!P1_ID!,\"ticker\":\"AAPL\"}" "%SERVER%/api/leagues/!LEAGUE_ID!/draft/pick" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" ( echo   [OK] Player 1 picked AAPL & set /a PASS+=1 ) else ( echo   [FAIL] Player 1 pick failed (HTTP %CODE%) & set /a FAIL+=1 )

curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"userId\":!P2_ID!,\"ticker\":\"TSLA\"}" "%SERVER%/api/leagues/!LEAGUE_ID!/draft/pick" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" ( echo   [OK] Player 2 picked TSLA & set /a PASS+=1 ) else ( echo   [FAIL] Player 2 pick failed (HTTP %CODE%) & set /a FAIL+=1 )

:: ── 9. Evaluate round ────────────────────────────────────────────────────────
echo [9/9] Evaluate round
curl -s -o "%TEMP%\ss_resp.json" -w "%%{http_code}" -X POST -H "Content-Type: application/json" -d "{\"hostId\":!P1_ID!,\"prices\":{\"AAPL\":195.50,\"TSLA\":245.00}}" "%SERVER%/api/leagues/!LEAGUE_ID!/evaluate" > "%TEMP%\ss_code.txt" 2>nul
set /p CODE=<"%TEMP%\ss_code.txt"
if "%CODE%"=="200" (
    echo   [OK] Round evaluated successfully
    echo.
    echo   Leaderboard:
    python -c "import json; entries=json.load(open(r'%TEMP%\ss_resp.json')); [print(f'    #{e[chr(114)+chr(97)+chr(110)+chr(107)]}  {e[chr(117)+chr(115)+chr(101)+chr(114)+chr(110)+chr(97)+chr(109)+chr(101)]:<20} {e[chr(116)+chr(105)+chr(99)+chr(107)+chr(101)+chr(114)]:<6} {e[chr(112)+chr(101)+chr(114)+chr(99)+chr(101)+chr(110)+chr(116)+chr(67)+chr(104)+chr(97)+chr(110)+chr(103)+chr(101)]:+.2f}%%') for e in entries]" 2>nul
    set /a PASS+=1
) else (
    echo   [FAIL] Evaluate round failed (HTTP %CODE%)
    set /a FAIL+=1
)

:summary
echo.
echo ===================================================
echo   Results: !PASS! passed, !FAIL! failed
echo ===================================================
echo.
