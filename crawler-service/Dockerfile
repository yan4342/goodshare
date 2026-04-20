FROM mcr.microsoft.com/playwright/python:v1.40.0-jammy

# Install Java 21 (Playwright image is based on Ubuntu/Debian, so we use apt)
# Use Tsinghua mirrors for apt
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    sed -i 's/archive.ubuntu.com/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list && \
    sed -i 's/security.ubuntu.com/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y openjdk-21-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Python dependencies
# Use Tsinghua mirror for pip
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    pip install playwright -i https://pypi.tuna.tsinghua.edu.cn/simple && \
    playwright install chromium

WORKDIR /app

COPY target/*.jar app.jar
# Copy scripts directory
COPY scripts/ /app/scripts/

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
