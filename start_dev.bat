@echo off
:: Batch script to start MySQL, Redis, Elasticsearch, and Frontend
:: Run as Administrator

:: Check for Administrator privileges
net session >nul 2>&1
if %errorLevel% == 0 (
    echo [INFO] Running with Administrator privileges.
) else (
    echo [WARN] Requesting Administrator privileges...
    powershell -Command "Start-Process '%0' -Verb RunAs"
    exit /b
)

echo ==================================================
echo Starting Services...
echo ==================================================

:: 1. Start Elasticsearch
echo [STEP 1] Starting Elasticsearch Service (elasticsearch-service-x64)...
net start elasticsearch-service-x64
if %errorLevel% neq 0 (
    echo [WARN] Failed to start Elasticsearch service. It might be already running or the service name is incorrect.
) else (
    echo [SUCCESS] Elasticsearch started.
)

:: 2. Start MySQL
echo [STEP 2] Starting MySQL Service (MySQL84)...
net start MySQL84
if %errorLevel% neq 0 (
    echo [WARN] Failed to start MySQL service. It might be already running.
) else (
    echo [SUCCESS] MySQL started.
)

:: 3. Start Redis
echo [STEP 3] Starting Redis Service...
:: Attempt to start as a service first (assuming name is "Redis")
net start Redis
if %errorLevel% neq 0 (
    echo [WARN] Could not start 'Redis' service. Attempting to run executable directly...
    start "Redis Server" "D:\redis\RedisService.exe" -c "D:\redis\redis.conf"
) else (
    echo [SUCCESS] Redis Service started.
)

:: 4. Start Frontend
echo [STEP 4] Starting Frontend (npm run dev)...
cd /d "%~dp0goodshare-web"
if exist package.json (
    echo Starting npm run dev in %CD%
    start "Frontend - Goodshare" npm run dev
    echo [SUCCESS] Frontend launched in a new window.
) else (
    echo [ERROR] package.json not found in goodshare-web!
    echo Current directory: %CD%
)

echo ==================================================
echo All startup tasks initiated.
echo ==================================================
pause
