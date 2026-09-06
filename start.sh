#!/bin/bash
# Konyaevo Bot — startup script for Linux/macOS
set -e
cd "$(dirname "$0")"

# Create .env from example if missing
if [ ! -f .env ]; then
    if [ -f .env.example ]; then
        cp .env.example .env
        echo "Created .env from .env.example. Please edit it with your BOT_TOKEN and restart."
        exit 1
    else
        echo "BOT_TOKEN=" > .env
        echo "Created empty .env. Please add your BOT_TOKEN and restart."
        exit 1
    fi
fi

echo "Starting Konyaevo Bot..."

JAR_FILE=""
for f in target/konyaevo-bot-*.jar konyaevo-bot-*.jar; do
    if [ -f "$f" ] && [[ "$f" != *.original ]]; then
        JAR_FILE="$f"
        break
    fi
done

if [ -z "$JAR_FILE" ]; then
    echo "ERROR: JAR file not found. Build the project first: ./mvnw clean package -DskipTests"
    exit 1
fi

exec java \
    -Xmx512m \
    -Duser.timezone=Europe/Moscow \
    -Dfile.encoding=UTF-8 \
    -jar "$JAR_FILE"
