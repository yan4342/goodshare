FROM python:3.11-alpine

# Install Java 21, Chromium, Chromedriver
# Use default Alpine repositories instead of mirrors to avoid sync issues
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
    openjdk21-jre \
    chromium \
    chromium-chromedriver \
    bash \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont \
    libxi \
    libxrender \
    libxtst

# Install Python dependencies
# Use Tsinghua mirror for pip
RUN pip install selenium webdriver-manager --break-system-packages -i https://pypi.tuna.tsinghua.edu.cn/simple

WORKDIR /app

COPY target/*.jar app.jar
# Copy scripts directory
COPY scripts/ /app/scripts/

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
