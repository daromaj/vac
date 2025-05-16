# VAC - Virtual Assistant for Calls

A simple Android application to assist with call screening.

## Development & Testing

### Prerequisites

*   Android Studio (latest stable version recommended)
*   Android SDK Platform Tools (for ADB)
*   A physical Android device or emulator for testing

### 1. Running Unit Tests

To run all unit tests for the project from the command line:

```bash
./gradlew test
```

To run unit tests for a specific module (e.g., the `app` module):

```bash
./gradlew :app:testDebugUnitTest
```

Or, to run tests for a specific class:

```bash
./gradlew :app:testDebugUnitTest --tests com.example.vac.activities.SetupActivityTest
```

### 2. Building the Application (APK)

You can build a debug APK for testing using either Android Studio or the command line.

**Using Android Studio:**

1.  Go to `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`.
2.  Android Studio will build the APK and provide a link to locate it.

**Using Command Line (Gradle):**

Navigate to the project's root directory and run:

```bash
./gradlew assembleDebug
```

The generated APK (`app-debug.apk`) will be located at: `app/build/outputs/apk/debug/app-debug.apk`

### 3. Installing the Application on a Device

#### Method A: Via Android Studio (Recommended for Development)

1.  Connect your Android device to your computer via USB.
2.  Ensure **Developer Options** and **USB Debugging** are enabled on your device.
3.  Authorize your computer for USB debugging on your device if prompted.
4.  In Android Studio, select your device from the target device dropdown.
5.  Click the **Run 'app'** button (the green play icon ▶️). Android Studio will build, install, and launch the app.

#### Method B: Manually Transferring and Installing APK

1.  Build the `app-debug.apk` as described in section 2.
2.  Connect your device via USB in "File Transfer" (MTP) mode.
3.  Copy the `app-debug.apk` from your computer (e.g., from `app/build/outputs/apk/debug/`) to a folder on your device (e.g., `Downloads`).
4.  On your device, use a file manager to navigate to the APK.
5.  Tap the APK to install. You may need to allow installations from "unknown sources" or for your file manager app.

#### Method C: Using ADB (Android Debug Bridge)

**Prerequisite:** Ensure ADB is installed and in your system's PATH. The `app-debug.apk` should be built.

**Option 1: ADB over USB**

1.  Connect your device via USB with USB Debugging enabled.
2.  Verify connection: `adb devices`
3.  Install APK:
    ```bash
    adb install path/to/your/app/build/outputs/apk/debug/app-debug.apk
    ```
    (If your terminal is in the project root, you can use the relative path: `adb install app/build/outputs/apk/debug/app-debug.apk`)

**Option 2: ADB over Wi-Fi (Android 11+ Recommended Method)**

1.  Ensure your device and computer are on the same Wi-Fi network.
2.  On your device:
    *   Go to `Settings` -> `Developer options`.
    *   Enable `Wireless debugging`.
    *   Tap on `Wireless debugging` (the text, not just the toggle).
    *   Select `Pair device with pairing code`.
    *   Note the **Wi-Fi pairing code** and the **IP address & port for pairing** displayed on your device.
3.  On your computer (terminal):
    *   `adb pair PAIRING_IP:PAIRING_PORT`
    *   Enter the **Wi-Fi pairing code** when prompted.
4.  After successful pairing, your device's "Wireless debugging" screen will show an active connection IP address and port (e.g., `YOUR_PHONE_IP:CONNECTION_PORT`).
5.  On your computer (terminal):
    *   `adb connect YOUR_PHONE_IP:CONNECTION_PORT`
6.  Verify connection: `adb devices` (should show your device with its IP).
7.  Install APK:
    ```bash
    adb install app/build/outputs/apk/debug/app-debug.apk
    ```

**Option 3: ADB over Wi-Fi (Older method, requires initial USB)**
1. Connect your device via USB with USB Debugging enabled.
2. In terminal: `adb tcpip 5555`
3. Disconnect USB.
4. Find your phone's IP address (e.g., in Wi-Fi settings).
5. In terminal: `adb connect YOUR_PHONE_IP:5555`
6. Verify connection: `adb devices`
7. Install APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ``` 