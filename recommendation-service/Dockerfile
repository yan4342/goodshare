# Use an official Python runtime as a parent image
FROM python:3.11-slim

# Set the working directory in the container
WORKDIR /app

# Set locale to UTF-8 for Chinese log output
ENV LANG=C.UTF-8

# Install system dependencies (needed for some python packages like numpy/pandas)
# netcat is often useful for waiting for DB
# Unset proxy to avoid localhost issues
# If you want to use proxy (e.g. host.docker.internal:7897), remove 'unset ...' and add 'ENV http_proxy=http://host.docker.internal:7897'
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    apt-get update && apt-get install -y \
    gcc \
    default-libmysqlclient-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

# Copy the requirements file into the container at /app
COPY requirements.txt .

# Install any needed packages specified in requirements.txt
# Use Tsinghua mirror for pip
# If you want to use proxy, remove 'unset ...' and '-i ...'
RUN unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY && \
    pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

# Copy the current directory contents into the container at /app
COPY . .

# Make port 5000 available to the world outside this container
EXPOSE 5000

# Run app.py when the container launches
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "5000"]
