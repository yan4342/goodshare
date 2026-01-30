@echo off
echo Starting Goodshare Docker Production Environment...

echo 1. Starting Local MySQL (Required for Docker containers)...
net start MySQL84
if %errorLevel% neq 0 (
    echo [WARN] Failed to start MySQL service. It might be already running.
) else (
    echo [SUCCESS] MySQL started.
)

echo 2. Starting All Services in Docker (Infrastructure + Microservices + Frontend)...
docker-compose -f docker-compose-prod.yml up -d

echo 3. Waiting for services to initialize...
echo Gateway will be available at: http://localhost:8080
echo Frontend will be available at: http://localhost:8088

echo Done! Use 'docker-compose -f docker-compose-prod.yml logs -f' to view logs.
pause
