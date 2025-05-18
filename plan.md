# MVP Implementation Plan

**IMPORTANT: All development must strictly adhere to the architecture, class designs, and interaction flows detailed in the `design.md` document.**

## Phase 1: Initial Setup & Core UI

### Task 1.1: Project Foundation
- [x] **Owner:** DEV
- **Description:** Create a new Android Studio project. Configure `build.gradle` with necessary dependencies (e.g., Kotlin standard library, basic AndroidX libraries). Set up basic project structure (directories for services, activities, utils).
- **Acceptance Criteria:**
    - Project compiles successfully.
    - Basic app icon and name are set.
    - Run all unit tests (if any exist at this stage) to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - N/A (Project setup verification)

### Task 1.2: Setup Screen UI & Logic
- [x] **Owner:** DEV
- **Description:** Implement the UI for the Setup screen. This screen will allow users to input their name for the greeting and optionally customize the greeting text. It will also be the central point for permission requests and language pack checks.
- **Acceptance Criteria:**
    - UI contains input fields for user's name and greeting text.
    - Values entered are persisted (e.g., using SharedPreferences) for MVP.
    - A default greeting is pre-filled if user hasn't set one.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_saveAndLoadUserName()`: Verify name is saved and loaded correctly.
    - `test_saveAndLoadGreetingText()`: Verify custom greeting is saved/loaded.
    - `test_defaultGreetingIsUsed()`: Verify default greeting loads if no custom one exists.

### Task 1.3: Permissions Handling
- [x] **Owner:** DEV
- **Description:** Implement permission requests for `RECORD_AUDIO`, `READ_PHONE_STATE` (and any others identified for `CallScreeningService`) on the Setup screen. If permissions are not already granted, they should be automatically requested when the Setup screen is first launched. A dedicated button to re-request permissions should also be available. Provide brief explanations for why permissions are needed.
- **Acceptance Criteria:**
    - App automatically requests necessary permissions when Setup screen is first launched, if not already granted.
    - A dedicated button also allows the user to trigger permission requests.
    - Permission status is reflected on the Setup screen (e.g., features disabled if not granted).
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_checkPermissionsGranted()`: Mock permission status to granted.
    - `test_checkPermissionsDenied()`: Mock permission status to denied.
    - (UI tests would be better here, but for unit tests, focus on the logic that checks status).

### Task 1.4: Polish Language Pack Check
- [x] **Owner:** DEV
- **Description:** On the Setup screen, implement logic to check if the Polish (`pl-PL`) offline speech recognition model is available. If not, guide the user to Android's voice input settings.
- **Acceptance Criteria:**
    - App correctly detects if `pl-PL` STT model is available.
    - If model is missing, a message is shown on the Setup screen.
    - A button/link attempts to navigate the user to `Settings.ACTION_VOICE_INPUT_SETTINGS`.
    - STT-dependent features are visually indicated as disabled if model is unavailable.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_languagePackAvailable()`: Mock `SpeechRecognizer.isRecognitionAvailable()` to return true for `pl-PL`.
    - `test_languagePackMissing()`: Mock `SpeechRecognizer.isRecognitionAvailable()` to return false.
    - `test_intentToVoiceSettingsIsCorrect()`: Verify the Intent to open settings is correctly formed.

## Phase 2: QA - Setup & Initial Configuration

### Task 2.1: Setup Screen & Permissions Verification
- [x] **Owner:** QA (USER)
- **Description:** Verify the Setup screen functionality: user name input, custom greeting input, permission request flow, and Polish language pack detection/guidance.
- **Acceptance Criteria:**
    - User can input and save their name.
    - User can input and save a custom greeting, or default is used.
    - Permission prompts appear with explanations; denying permissions disables relevant (conceptual) features correctly on the Setup screen.
    - Language pack check works; guidance to settings is functional.
    - All tests performed on Samsung Galaxy S21 Ultra (Android 14).

## Phase 3: Core Call Handling

### Task 3.1.1: `CallScreeningService` Stub & Manifest Declaration
- [x] **Owner:** DEV
- **Description:** Create the `CallScreeningServiceImpl.java` file, making it extend `android.telecom.CallScreeningService`. Implement the required `onScreenCall` method with a basic stub (e.g., just logging for now). Declare this service correctly in the `AndroidManifest.xml` with the `BIND_SCREENING_SERVICE` permission and the appropriate intent filter (`<action android:name="android.telecom.CallScreeningService" />`).
- **Acceptance Criteria:**
    - `CallScreeningServiceImpl.java` exists and compiles.
    - Service is declared in `AndroidManifest.xml` with necessary permission and intent filter.
    - The app appears in the list of possible call screening apps in Android settings.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - Manual check in Android settings to see if the app is listed.
    - Code review for manifest and service structure.

### Task 3.1.2: Default Call Screening App & `onScreenCall` Invocation
- [x] **Owner:** DEV
- **Description:** Ensure the app can be reliably selected and function as the default Call Screening application. This might involve adding a helper UI in `SetupActivity` (e.g., a button/status text) that directs the user to the system settings to select the app as the default call screener if it isn't already. Verify that `onScreenCall()` in `CallScreeningServiceImpl` is invoked when an incoming call is made while the app is the default screener.
- **Acceptance Criteria:**
    - App can be selected as the default Call Screening application through Android settings.
    - `CallScreeningServiceImpl.onScreenCall()` is demonstrably invoked (e.g., via logging or a Toast for now) when an incoming call occurs and the app is the default screener.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - Manual testing: Set app as default, make incoming call, check logs/Toast.

### Task 3.1.3: Foreground Service Initiation & Basic Notification
- [x] **Owner:** DEV
- **Description:** Within `CallScreeningServiceImpl` (likely triggered from `onScreenCall`), implement the logic to start a foreground service. This service will eventually manage the active call session details. For this task, just focus on starting the service and displaying a *very basic* persistent notification (e.g., "VAC is screening a call"). This requires the `FOREGROUND_SERVICE` permission in the manifest and potentially a more specific foreground service type for Android 10+ (e.g. `phoneCall` for Android Q/10, `mediaPlayback` if using media, or checking for newer types like `microphone` for Android S/12 or `camera` if relevant. For Android 14, `FOREGROUND_SERVICE_PHONE_CALL` or `FOREGROUND_SERVICE_SPECIAL_USE` with appropriate declarations might be needed. We will start with `FOREGROUND_SERVICE` and refine the type as required by Android version compatibility and functionality).
- **Acceptance Criteria:**
    - A foreground service is started by `CallScreeningServiceImpl` when `onScreenCall` processes a call.
    - A basic, persistent notification is displayed for this foreground service.
    - Necessary foreground service permissions are declared in the manifest.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - Manual testing: Make an incoming call that triggers screening. Verify the foreground service starts and its notification appears.

### Task 3.2: Incoming Call Trigger & Assistant Activation
- [x] **Owner:** DEV
- **Description:** Ensure that when an incoming call arrives, the `CallScreeningService` intercepts it and the assistant's workflow begins. This includes the "Answer with Assistant" functionality.
- **Acceptance Criteria:**
    - Incoming calls correctly trigger the `CallScreeningService`.
    - The app takes control to manage the call audio for the assistant's interaction.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - (Difficult to unit test directly; relies on system integration. Focus on components called by the service).

### Task 3.3: Assistant Greeting Playback
- [x] **Owner:** DEV
- **Description:** Implement assistant greeting playback using `TextToSpeech` (TTS) or `MediaPlayer` for pre-recorded audio. The greeting should use the user's name from Setup and include the mandatory "This call is being recorded" disclosure.
- **Acceptance Criteria:**
    - Greeting plays clearly when the assistant answers.
    - Greeting dynamically includes the user's name if provided.
    - Greeting includes "This call is being recorded."
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_greetingPlaysWithUserName()`: Verify TTS output string contains the name.
    - `test_greetingIncludesRecordingNotice()`: Verify TTS output string includes the notice.
    - `test_mediaPlayerPlaysGreeting()`: If using `MediaPlayer`, mock and verify `start()` is called.

## Phase 4: QA - Call Triggering & Greeting

### Task 4.1: Call Screening & Greeting Verification
- [x] **Owner:** QA (USER)
- **Description:** Verify that incoming calls are correctly screened by the assistant and the initial greeting is played as configured.
- **Acceptance Criteria:**
    - On an incoming call, the assistant answers (or provides UI to answer with assistant).
    - The configured greeting (with user's name and recording notice) plays clearly.
    - Regular calls (not answered by assistant) are unaffected.
    - All tests performed on Samsung Galaxy S21 Ultra (Android 14).

### Advanced Greeting Setup (Optional MVP Extension)

#### Task 4.1.1: UI for Custom Greeting Generation and Playback Controls
- [x] **Owner:** DEV
- **Description:** Add new UI elements to `SetupActivity` for managing a pre-recorded greeting. This includes:
    - A button labeled "Generate Greeting File".
    - A button labeled "Play Generated Greeting".
    - Potentially a `TextView` to indicate the status or filename of the generated greeting.
- **Acceptance Criteria:**
    - New buttons ("Generate Greeting File", "Play Generated Greeting") are present and visible in `SetupActivity`'s layout.
    - Associated UI elements (like a status TextView) are present if designed.
    - These UI elements do not yet need to be functional; this task is for layout and resource creation only.
    - App compiles, and existing unit tests pass.
- **Test Scenarios (Unit Tests):**
    - Visual inspection of `SetupActivity` layout.
    - (No specific unit tests for non-functional UI elements, but existing tests should not break).

#### Task 4.1.2: Implement "Generate Greeting File" Logic
- [x] **Owner:** DEV
- **Description:** Implement the functionality for the "Generate Greeting File" button in `SetupActivity`. This involves:
    - Retrieving the current user name and base greeting text (respecting defaults or user customizations for live TTS).
    - Constructing the full greeting string (including "This call is being recorded").
    - Using `TextToSpeech.synthesizeToFile()` (likely via a new method in `AudioHandler`) to save the synthesized audio to an app-specific file (e.g., `custom_greeting.wav` or `.mp3`).
    - Updating the status `TextView` to show success (e.g., "Greeting file generated: custom_greeting.wav") or failure.
    - Storing a preference (e.g., in `PreferencesManager`) indicating that a custom greeting file exists and should potentially be used.
- **Acceptance Criteria:**
    - Tapping "Generate Greeting File" button triggers TTS synthesis to a file.
    - An audio file is saved in app-specific storage.
    - Status UI is updated on success or failure.
    - A preference is saved to note the existence/path of the custom file.
    - App compiles, and existing unit tests pass.
- **Test Scenarios (Unit Tests):**
    - `test_generateGreetingFile_success()`: Verify TTS `synthesizeToFile` is called, file path is generated, preference is saved, UI status updates.
    - `test_generateGreetingFile_ttsFailure()`: Verify error is handled, UI status updates.

#### Task 4.1.3: Implement "Play Generated Greeting" Logic
- [x] **Owner:** DEV
- **Description:** Implement the functionality for the "Play Generated Greeting" button in `SetupActivity`. This involves:
    - Checking if a custom greeting file exists (based on preference or file system check).
    - If it exists, using `MediaPlayer` (likely via `AudioHandler.playAudioFile()`) to play back the saved audio file.
    - If it doesn't exist, disable the button or show a `Toast` "No generated greeting file found."
- **Acceptance Criteria:**
    - Tapping "Play Generated Greeting" plays the saved audio file if it exists.
    - If no file exists, appropriate feedback is given.
    - App compiles, and existing unit tests pass.
- **Test Scenarios (Unit Tests):**
    - `test_playGeneratedGreeting_fileExists()`: Verify `AudioHandler.playAudioFile()` is called with the correct file URI.
    - `test_playGeneratedGreeting_noFile()`: Verify playback is not attempted, and UI feedback occurs.
    - `test_playGeneratedGreeting_fileNotFound()`: Verify appropriate feedback if file path is invalid.

#### Task 4.1.4: Integrate Generated Greeting into Call Screening Flow
- [x] **Owner:** DEV
- **Description:** Modify `CallSessionManager` to use the generated audio file for the greeting if the user has enabled this option and the file exists. Otherwise, fall back to live TTS. This also involves adding a UI preference (e.g., a Switch) in `SetupActivity` to control this behavior.
- **Acceptance Criteria:**
    - If "use custom greeting file" is enabled and file exists, `CallSessionManager` uses the file for the greeting during a call.
    - Otherwise, live TTS is used as before.
    - A UI element (e.g., Switch) in `SetupActivity` allows enabling/disabling the use of the generated file.
    - App compiles, and existing unit tests pass.
    - `test_callUsesLiveTTS_ifGeneratedFileMissing()` (even if enabled).

#### Task 4.1.5: Unit Tests for Custom Greeting Feature
- [x] **Owner:** DEV
- **Description:** Ensure comprehensive unit tests are created for all new logic introduced in tasks 4.1.2, 4.1.3, and 4.1.4. This includes testing interactions with `AudioHandler`, `PreferencesManager`, file system (mocked), and `SetupActivity` logic.
- **Acceptance Criteria:**
    - All new functionalities related to custom greeting generation, playback, and integration have corresponding unit tests.
    - All unit tests pass.

## Phase 5: Speech-to-Text (STT) & Caller Interaction

### Task 5.1: `SpeechRecognizer` Integration (via `SpeechRecognitionHandler`)
- [x] **Owner:** DEV
- **Description:** Implement `SpeechRecognitionHandler` as per `design.md`. Initialize and manage `SpeechRecognizer` for `pl-PL` locale to capture the caller's speech after the greeting.
- **Acceptance Criteria:**
    - `SpeechRecognitionHandler` correctly initializes `SpeechRecognizer`.
    - `SpeechRecognizer` starts listening after the assistant's greeting, as orchestrated by `CallSessionManager`.
    - `SpeechRecognizer` is configured for `pl-PL`.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_speechRecognizerStartsListening()`: Verify `SpeechRecognitionHandler` invokes `startListening()` on `SpeechRecognizer`.
    - `test_speechRecognizerUsesPolishLocale()`: Verify the Intent passed to `startListening()` has `EXTRA_LANGUAGE` set to "pl-PL".

### Task 5.2: STT Callbacks & Stop Detection (via `SpeechRecognitionHandler`)
- [x] **Owner:** DEV
- **Description:** Implement `RecognitionListener` within `SpeechRecognitionHandler` to handle STT results, errors, and end-of-speech. Implement the 2-3 second silence timeout logic within `CallSessionManager` after `onEndOfSpeech()`.
- **Acceptance Criteria:**
    - `SpeechRecognitionHandler` correctly relays `onResults()` to `CallSessionManager`.
    - `SpeechRecognitionHandler` gracefully handles `onError()` and informs `CallSessionManager`.
    - `CallSessionManager` correctly implements the silence timeout post `onEndOfSpeech()`.
    - When user takes over, `SpeechRecognitionHandler.stopListening()` is called, halting new audio intake. Buffered audio processing (if any) can complete as per design.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_onResultsUpdatesTranscriptionViaListener()`.
    - `test_onErrorNotifiesCallSessionManager()`.
    - `test_callSessionManagerHandlesSTTTimeout()`.
    - `test_stopListeningHaltsNewAudioIntake()`.

### Task 5.3: Live Transcription & Call Control Notification (via `NotificationHandler`)
- [x] **Owner:** DEV
- **Description:** Implement `NotificationHandler` as per `design.md`. Display live transcription snippets and a "Take Over Call" button via a persistent foreground service notification managed by `CallScreeningServiceImpl`.
- **Acceptance Criteria:**
    - A foreground service notification is displayed by `CallScreeningServiceImpl` (using `NotificationHandler`) during an active assistant call.
    - Transcribed speech snippets from `SpeechRecognitionHandler` (via `CallSessionManager`) update this notification in near real-time.
    - Notification includes a "Take Over Call" button.
    - When "Take Over Call" is pressed, the notification UI updates (e.g., button changes/disappears, text indicates user control).
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_notificationIsShownOnCallStart()`: Verify `NotificationHandler` methods are called.
    - `test_notificationUpdatesWithTranscription()`.
    - `test_notificationReflectsUserTakeOverState()`.

### Task 5.4: User Take-Over Call Logic
- [x] **Owner:** DEV
- **Description:** Implement the full "User Takes Over Call" functionality as detailed in `design.md`. This involves `CallScreeningServiceImpl` handling the notification action and instructing `CallSessionManager`.
- **Acceptance Criteria:**
    - Tapping "Take Over Call" button triggers `CallSessionManager.userTakesOver()`.
    - Assistant TTS (`AudioHandler.stopPlayback()`) stops immediately.
    - `SpeechRecognitionHandler.stopListening()` is called (new audio intake stops).
    - `MessageRecorderHandler` *continues* recording.
    - `CallSessionManager` state changes to prevent new assistant responses.
    - User can speak and hear on the call normally via standard telecom audio paths.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_userTakeOverStopsAssistantTTS()`.
    - `test_userTakeOverStopsNewSTTListening()`.
    - `test_userTakeOverKeepsRecordingActive()`.
    - `test_callSessionManagerStateChangesOnTakeOver()`.

## Phase 6: Message Handling

### Task 6.1: Predefined Follow-up Response
- [x] **Owner:** DEV
- **Description:** After STT (caller finishes speaking or timeout), play a predefined follow-up message using TTS/MediaPlayer (e.g., "Thank you. [User's Name] is not available right now. Would you like to leave a message?").
- **Acceptance Criteria:**
    - Follow-up message plays clearly after STT phase.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_followUpMessagePlays()`: Verify TTS/MediaPlayer is invoked for the follow-up.

### Task 6.2: Caller Message Recording (via `MessageRecorderHandler`)
- [x] **Owner:** DEV
- **Description:** Implement `MessageRecorderHandler` to record entire call from start to finish. Recording continues even if user takes over the call, until call ends.
- **Acceptance Criteria:**
    - Recording starts when call is answered.
    - Recording stops when call ends.
    - Audio is saved to a file in app-private storage.
    - `MessageRecorderHandler.stopRecording()` is correctly called by `CallSessionManager.stopScreening()` when the call finally terminates.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_mediaRecorderStarts()`: Verify `MediaRecorder.start()` is called.
    - `test_audioFileIsSaved()`: Check if file path generation is correct (actual saving is integration).
    - `test_recordingContinuesOnUserTakeOver()`.
    - `test_recordingStopsWhenCallEndsPostTakeOver()`.

### Task 6.3: Basic Message Playback UI
- [x] **Owner:** DEV
- **Description:** Implement a very basic UI (e.g., a list in an Activity) for the user to see and playback recorded messages.
- **Acceptance Criteria:**
    - User can see a list of recorded messages (e.g., by timestamp/filename).
    - User can tap a message to play it back using `MediaPlayer`.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_loadMessageList()`: Verify logic that lists saved audio files.
    - `test_playMessageStartsMediaPlayer()`: Verify `MediaPlayer` is used for playback.

## Phase 7: Transcription Saving & Display

### Task 7.1: Transcription Data Structure & Storage
- [x] **Owner:** DEV
- **Description:** Implement the `TranscriptionData` class and `TranscriptionManager` for saving and managing transcriptions. This includes:
    - Creating the data structure for transcription snippets
    - Implementing JSON file storage for transcriptions
    - Adding methods to associate transcriptions with audio recordings
- **Acceptance Criteria:**
    - `TranscriptionData` class correctly stores timestamp, text, and callId
    - Transcriptions are saved in a structured JSON format
    - Each transcription snippet is properly associated with its audio recording
    - Transcriptions persist across app restarts
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_transcriptionDataStructure()`: Verify data class properties
    - `test_transcriptionJsonSerialization()`: Verify JSON format
    - `test_transcriptionPersistence()`: Verify data survives app restart
    - `test_transcriptionAudioAssociation()`: Verify correct audio-transcription pairing

### Task 7.2: Audio Level Monitoring
- [x] **Owner:** DEV
- **Description:** Implement `AudioLevelMonitor` to track local microphone audio levels. This includes:
    - Setting up audio level monitoring using Android's `AudioRecord`
    - Implementing threshold detection for user speech
    - Providing real-time audio level data
- **Acceptance Criteria:**
    - Audio levels are monitored continuously during calls
    - User speech is reliably detected via threshold
    - Monitoring doesn't interfere with other audio operations
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_audioLevelMonitoring()`: Verify level detection works
    - `test_userSpeechDetection()`: Verify threshold detection
    - `test_monitoringPerformance()`: Verify no impact on other audio

### Task 7.3: Speaker Identification
- [x] **Owner:** DEV  // Marked as complete based on user feedback
- **Description:** Implement `SpeakerIdentifier` to determine the speaker for each transcription. This includes:
    - Using audio levels to identify user speech
    - Tracking TTS state for assistant identification
    - Handling user take-over state
    - Determining caller speech by process of elimination
- **Acceptance Criteria:**
    - Speaker type is correctly identified for each transcription
    - Transitions between speakers are handled smoothly
    - Edge cases (e.g., overlapping speech) are handled gracefully
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_speakerIdentification()`: Verify correct speaker assignment
    - `test_speakerTransitions()`: Verify smooth transitions
    - `test_edgeCases()`: Verify handling of edge cases

### Task 7.4: Real-time Transcription Saving with Speaker Info
- [x] **Owner:** DEV
- **Description:** Modify `SpeechRecognitionHandler` to save transcriptions via `TranscriptionManager`. This includes:
    - Adding transcription saving to the STT callback flow
    - Ensuring transcriptions are saved even during user take-over
    - Implementing proper error handling for storage failures
- **Acceptance Criteria:**
    - Each STT result is saved with its timestamp
    - Transcriptions continue to be saved during user take-over
    - Storage errors are handled gracefully
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_sttResultsAreSaved()`: Verify transcription saving from STT
    - `test_transcriptionDuringTakeOver()`: Verify saving during user control
    - `test_storageErrorHandling()`: Verify graceful error handling

### Task 7.5: Enhanced Message Playback UI with Transcriptions
- [x] **Owner:** DEV
- **Description:** Implement the enhanced message playback UI with transcription display. This includes:
    - Creating `TranscriptionPlaybackManager` for audio-text synchronization
    - Updating the message playback UI to show transcriptions
    - Adding search functionality for transcriptions
- **Acceptance Criteria:**
    - Transcriptions are displayed alongside audio playback
    - Current playback position is highlighted in the transcription
    - Users can search through transcriptions
    - UI is responsive and user-friendly
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_playbackTranscriptionSync()`: Verify audio-text synchronization
    - `test_transcriptionSearch()`: Verify search functionality
    - `test_playbackPositionHighlight()`: Verify position indication
    - `test_uiResponsiveness()`: Verify UI performance

## Phase 8: Implement Reusable AudioPlayer with Controls

### Task 8.1: Enhance Audio Playback Component
- **Owner:** DEV
- **Description:** Extend AudioHandler to create a reusable AudioPlayer with controls (progress bar for scrubbing, play/pause buttons, timer display). Use a DialogFragment for the pop-up UI, including a close button for easy dismissal. Ensure it handles playback for both greetings and recorded calls, with graceful stopping on errors or interruptions.
- **Acceptance Criteria:**
  - DialogFragment displays controls and plays audio correctly.
  - SeekBar allows scrubbing to specific seconds.
  - Timer shows file length and current position.
  - Close button dismisses the dialog.
  - Playback stops gracefully on errors (e.g., file not found) or interruptions.
  - Run all unit tests to ensure no existing functionality is broken.
  - If all tests pass, commit changes before proceeding.

### Task 8.2: Unit Tests for AudioPlayer
- **Owner:** DEV
- **Description:** Add unit tests for the new AudioPlayer functionality.
- **Acceptance Criteria:**
  - Tests cover play, pause, scrubbing, and timer updates.
  - All tests pass.

## Phase 9: QA - STT, Recording & Playback

### Task 8.1: Full Interaction Flow Verification
- [ ] **Owner:** QA (USER)
- **Description:** Verify the STT functionality, transcription display, follow-up message, message recording, and playback of recorded messages.
- **Acceptance Criteria:**
    - Caller's speech is transcribed to the notification.
    - Silence timeout for STT functions correctly.
    - Follow-up message plays.
    - Message recording works as intended (entire call duration).
    - Recorded messages can be listed and played back in the app.
    - All tests performed on Samsung Galaxy S21 Ultra (Android 14).

## Phase 10: Stability & Polish

### Task 9.1: Audio Focus Management
- [ ] **Owner:** DEV
- **Description:** Implement robust audio focus management using `AudioManager` to prevent conflicts between TTS, STT, `MediaPlayer` (for greetings/playback), and the call audio itself.
- **Acceptance Criteria:**
    - App correctly requests and abandons audio focus for each audio operation.
    - No audible conflicts or unexpected stopping of audio streams.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_requestsAudioPlaybackFocus()`: Mock `AudioManager` and verify `requestAudioFocus()` call.
    - `test_abandonsAudioPlaybackFocus()`: Mock `AudioManager` and verify `abandonAudioFocus()` call.

### Task 9.2: Resource Cleanup
- [ ] **Owner:** DEV
- **Description:** Ensure all resources (`MediaPlayer`, `SpeechRecognizer`, `TextToSpeech`, `MediaRecorder`, services, handlers) are properly released when they are no longer needed, or when the call/service ends. This is critical for normal call termination and when the user takes over (cleanup occurs upon actual call end).
- **Acceptance Criteria:**
    - `release()`, `destroy()`, `stopSelf()` etc., are called appropriately in all lifecycle methods and relevant call flow terminations (including post user take-over call end).
    - No resource leaks are evident during testing.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_componentsReleasedOnDestroy()`: For Activities/Services, verify release methods are called in `onDestroy()`.
    - `test_resourcesReleasedPostTakeOverOnCallEnd()`.

### Task 9.3: MVP Error Handling
- [ ] **Owner:** DEV
- **Description:** Implement basic `Toast` messages for critical operational failures as per MVP clarifications (e.g., "Audio recording failed," "Speech engine unavailable"). Implement logging for key events and errors.
- **Acceptance Criteria:**
    - User sees Toasts for defined critical errors.
    - Key app events and errors are logged via `Log.d`/`Log.e`.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit the changes with a descriptive message before proceeding to the next task.
- **Test Scenarios (Unit Tests):**
    - `test_errorToastIsShown()`: Simulate error condition, verify Toast display logic (if possible in unit test, otherwise manual).

## Phase 11: Final MVP Testing

### Task 10.1: Integration & Device Testing
- [ ] **Owner:** DEV
- **Description:** Perform overall integration testing of all features on the Samsung Galaxy S21 Ultra (Android 14). Ensure all MVP acceptance criteria from `design.md` are met.
- **Acceptance Criteria:**
    - All MVP features function cohesively on the target device.
    - App is stable during typical use flows.
    - Run all unit tests to ensure no existing functionality is broken by the changes.
    - If all tests pass, commit any final code tweaks/fixes with a descriptive message.

## Phase 12: QA - Final MVP Acceptance

### Task 11.1: End-to-End MVP Flow & Stability
- [ ] **Owner:** QA (USER)
- **Description:** Conduct end-to-end testing of all features of the MVP application on the Samsung Galaxy S21 Ultra (Android 14). Verify stability, resource handling, error messages, and overall user experience for the MVP.
- **Acceptance Criteria:**
    - The app successfully screens calls, plays greetings, transcribes speech, plays follow-ups, records messages, and allows playback as designed for MVP.
    - The app is stable and does not crash during defined test scenarios.
    - Resource management seems appropriate (no excessive battery drain noted during a few test calls).
    - Error handling (Toasts for critical issues) works as expected.
    - All acceptance criteria outlined in `design.md` (MVP sections) and this plan are met.
