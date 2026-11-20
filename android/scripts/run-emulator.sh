#!/usr/bin/env bash
# Installs the debug APK on the running emulator/device, launches it, takes a screenshot.
A=/home/pietro/Android/Sdk/platform-tools/adb
APK=/home/pietro/hackathon/ixh26/android/app/build/outputs/apk/debug/app-debug.apk
$A wait-for-device
until [ "$($A shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
$A install -r "$APK" && $A shell pm grant com.example.photoanchor android.permission.CAMERA
$A logcat -c
$A shell am start -n com.example.photoanchor/.MainActivity
sleep 5
$A exec-out screencap -p > /tmp/s1.png
$A logcat -d -s AndroidRuntime:E | tail -20
echo done
