FROM python:3.11-alpine

# Install Java 21, Chromium, Chromedriver
# Use USTC mirrors
# If you want to use proxy (e.g. host.docker.internal:7897), remove 'unset ...' and add 'ENV http_proxy=http://host.docker.internal:7897'
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    sed -i 's/dl-cdn.alpinelinux.org/mirrors.ustc.edu.cn/g' /etc/apk/repositories && \
    apk update && \
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
# If you want to use proxy, remove 'unset ...' and '-i ...'
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    pip install selenium webdriver-manager --break-system-packages -i https://pypi.tuna.tsinghua.edu.cn/simple

WORKDIR /app

COPY target/*.jar app.jar
# Copy scripts directory
COPY scripts/ /app/scripts/

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
