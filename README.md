# SITConnect - C2 Server & Trojanised Mobile Application

A school project developed for **ICT2215 Mobile Security** at Singapore Institute of Technology. The project demonstrates mobile threat techniques by integrating covert capabilities into a legitimate-looking Android application, alongside a real-time C2 server for remote management.

> **Educational Use Only**: This project was built for a school project and is not intended for use against any real device or system without explicit authorisation. Unauthorised use may be illegal.

---

## Project Structure

```
SITConnect/
├── app/              # Trojanised Android app (Kotlin, Jetpack Compose)
├── C2Server/         # C2 server (Python, Flask, Flask-SocketIO)
├── force-sitconnect-tracking.sh   # ADB root script
└── deploy-user-creation.sh        # ADB deployment helper
```

---

## C2 Server

Built with **Python**, **Flask**, and **Flask-SocketIO**, featuring a real-time web dashboard.

**Features:**
- Live client map showing connected devices and their GPS coordinates
- Interactive command console for issuing commands to connected devices
- Multi-client management across dual socket ports (command port and screen stream port)
- Remote file download from connected devices

---

## Android App

Built with **Kotlin** and **Jetpack Compose**, malicious functionalities are integrated into a legitimate-looking SIT student app.

**Covert capabilities:**
- Silent GPS tracking
- File exfiltration
- Device and network reconnaissance (battery, RAM, storage, installed apps, network info)
- Live screen capture (requires rooted device — see below)

**Persistence mechanisms:**
- Wake locks and Wi-Fi locks to prevent the process from being killed
- Boot receiver to restart the app after device reboot
- Alarm-based watchdog restarts
- Process locking to prevent duplicate instances

---

## Test Environments

The project was tested on:
- **Android Emulator** (Pixel AVD) — root access enabled via `adb root`
- **Rooted Pixel device** — rooted physical device with root access via `su`

## Root Script Features

`force-sitconnect-tracking.sh` is a boot script for both the Android emulator and rooted physical device. It:
- Suppresses Android privacy indicators (screen capture chip, camera/mic icons, location indicator)
- Force-grants location, screen projection, and file access permissions via AppOps
- Adds the app to the device idle whitelist to prevent system-level process kills

### Deployment Steps

```bash
adb push <filename>.sh /sdcard/
adb shell
su
mv /sdcard/<filename>.sh /data/adb/service.d/<filename>.sh
chmod 755 /data/adb/service.d/<filename>.sh
exit
exit
```

The script runs automatically at boot.

---

## Tech Stack

| Component     | Technologies                          |
|---------------|---------------------------------------|
| C2 Server     | Python, Flask, Flask-SocketIO         |
| Android App   | Kotlin, Jetpack Compose, Android SDK  |


