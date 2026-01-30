@echo off
echo Starting Goodshare Microservices...

echo 1. Starting Infrastructure (Nacos, Redis, Elasticsearch)...
docker-compose up -d

echo Starting MySQL84
net start MySQL84
if %errorLevel% neq 0 (
    echo [WARN] Failed to start MySQL service. It might be already running.
) else (
    echo [SUCCESS] MySQL started.
)

echo Waiting for Nacos to start...
timeout /t 15

echo 2. Starting Gateway Service (8080) [Nacos: 8500]...
start "Gateway Service" cmd /k "cd gateway-service && mvn spring-boot:run"

echo 3. Starting Core Service (8081)...
start "Core Service" cmd /k "cd goodshare-server && mvn spring-boot:run"

echo 4. Starting Crawler Service (8082)...
start "Crawler Service" cmd /k "cd crawler-service && mvn spring-boot:run"

echo 5. Starting Recommendation Service (5000)...
start "Recommendation Service" cmd /k "cd recommendation-service && pip install -r requirements.txt && python app.py"

echo 6. Starting Frontend (Web)...
start "Frontend" cmd /k "cd goodshare-web && npm run dev"

echo All services initiated!
echo Gateway: http://localhost:8080
echo Frontend: http://localhost:5180
pause
