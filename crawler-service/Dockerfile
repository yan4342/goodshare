FROM python:3.11-alpine

# Install Java 21, Chromium, Chromedriver
# Replace default repositories with Aliyun mirror to improve speed and stability
# Use sed to preserve the alpine version defined in the base image
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    sed -i 's/https/http/g' /etc/apk/repositories && \
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
# Use Aliyun mirror for pip
RUN pip install selenium webdriver-manager --break-system-packages -i https://mirrors.aliyun.com/pypi/simple/

WORKDIR /app

COPY target/*.jar app.jar
# Copy scripts directory
COPY scripts/ /app/scripts/

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
