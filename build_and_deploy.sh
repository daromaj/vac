#!/bin/bash
# Script to clean, build the APK, and deploy to the phone using ADB

echo "Cleaning the project..."
./gradlew clean

echo "Building the debug APK..."
./gradlew assembleDebug

echo "Deploying the APK to the connected device..."
adb install app/build/outputs/apk/debug/app-debug.apk

echo "Process completed."
