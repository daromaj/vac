#!/bin/bash
# Script to clean, build the APK, and deploy to the phone using ADB

echo "Cleaning the project..."
./gradlew clean

echo "Building the debug APK..."
./gradlew assembleDebug

echo "Deploying the APK to the connected device..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "Process completed. Script will exit in 3 seconds..."
pause 3

