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
    adb install app/build/outputs/apk/debug/app-debug.apk
    ```

**Option 2: ADB over Wi-Fi (Android 11+ Recommended Method)**

1.  Ensure your device and computer are on the same Wi-Fi network.
2.  On your device:
    *   Go to `Settings` -> `Developer options`.
    *   Enable `Wireless debugging`.
    *   Tap on `Wireless debugging` and pair with your computer.
3.  On your computer:
    *   `adb connect YOUR_DEVICE_IP:PORT`
    *   Install APK:
        ```bash
        adb install app/build/outputs/apk/debug/app-debug.apk
        ```

**Option 3: ADB over Wi-Fi (Older method)**

1.  Connect via USB and run: `adb tcpip 5555`
2.  Disconnect USB and run: `adb connect YOUR_DEVICE_IP:5555`
3.  Install APK:
    ```bash
    adb install app/build/outputs/apk/debug/app-debug.apk
    ```

### 4. Automation Scripts

We've added scripts to automate the build and deployment process:

- **build_and_deploy.sh**: For Unix-based systems. Run with `./build_and_deploy.sh`.
- **build_and_deploy.bat**: For Windows. Run with `build_and_deploy.bat`.

These scripts clean the project, build the debug APK, and deploy it using ADB.

### Recent Updates

- Implemented transcription handling in MessagesActivity for Task 7.5.
- Fixed getFormattedDate() in Message.java to parse and format timestamps from filenames.
- Ensured all unit tests pass after manual and automated fixes.
- Added reusable AudioPlayer with controls (progress bar, play/pause, timer) for greetings and recorded calls in Phase 8 of the plan.
- Implemented audio control methods (pause, resume, seekTo, getCurrentPosition, getDuration) in AudioHandler.java for Phase 8.
- Added UI controls for audio playback in MessagesActivity to address user feedback on visibility.
