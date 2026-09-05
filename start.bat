@echo off
cd /d "%~dp0"
title Konyaevo Bot

if not exist .env (
    if exist .env.example (
        copy .env.example .env >nul
    ) else (
        echo BOT_TOKEN=>.env
    )
    echo Opening .env, please insert your BOT_TOKEN and save the file...
    notepad .env
)

echo Starting Konyaevo Bot...
if exist "target\konyaevo-bot-1.0.0.jar" (
    java -jar "target\konyaevo-bot-1.0.0.jar"
) else (
    java -jar "konyaevo-bot-1.0.0.jar"
)

pause