# FloodComms Walkthrough

I have implemented the FloodComms offline communication app using React Native and Google Nearby Connections API.

## Implemented Features

### 1. Native Module (Android)
- **FloodCommsModule**: Handles P2P advertising and discovery using `Strategy.P2P_CLUSTER`.
- **AudioHelper**: Manages `AudioRecord` and `AudioTrack` for real-time voice communication.
- **Location**: Automatically fetches current location and embeds it into the audio payload.

### 2. React Native UI
- **PTT Interface**: Large "Hold to Talk" button.
- **Status Display**: Shows connection status (Broadcasting/Connected).
- **Location Display**: Shows the latitude/longitude of the sender when audio is received.

### 3. Privacy & Safety
- **Location Sharing**: As requested, location is automatically shared with every voice message to aid in rescue operations.
- **Audio Replay**: Received messages are saved to the device and can be replayed anytime.
- **Google Maps**: Received locations can be opened directly in Google Maps.
- **Robust Protocol**: Implemented a START/END protocol to ensure reliable bi-directional communication.

## Verification
The Android app has been successfully built (`assembleDebug`).

### Build Instructions
1.  **Configure SDK**: Ensure `ANDROID_HOME` is set or `local.properties` exists.
    ```bash
    export ANDROID_HOME=/Users/manojmadushanka/Library/Android/sdk
    ```
2.  **Build**:
    ```bash
    cd android
    ./gradlew assembleDebug
    ```
3.  **Run**:
    ```bash
    ```bash
    npx react-native run-android
    ```

### Alternative: Manual APK Installation (Offline Mode)
If you cannot connect your device via ADB or want to test without a running development server, you must bundle the JavaScript manually before building.

1.  **Bundle JS**:
    ```bash
    mkdir -p android/app/src/main/assets
    npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output android/app/src/main/assets/index.android.bundle --assets-dest android/app/src/main/res
    ```
2.  **Rebuild APK**:
    ```bash
    cd android
    ./gradlew clean assembleDebug
    ```
3.  **Install**: Transfer and install `android/app/build/outputs/apk/debug/app-debug.apk` on your device.

### Manual Verification Steps
1.  **Permissions**: Grant all requested permissions (Location, Microphone, Nearby Devices).
2.  **P2P Connection**:
    - Requires 2 physical Android devices.
    - Open the app on both devices.
    - They should automatically discover and connect to each other (Status: "Connected").
3.  **Audio & Location**:
    - Hold the "HOLD TO TALK" button on Device A.
    - Speak into the microphone.
    - Device B should play the audio and display Device A's location.
    - **New**: Device B will show a "Play Audio" button to replay the message.
    - **New**: Device B will show a "Map" button to open the location in Google Maps.

## Files Created
- `android/app/src/main/java/com/floodcomms/FloodCommsModule.kt`
- `android/app/src/main/java/com/floodcomms/AudioHelper.kt`
- `android/app/src/main/java/com/floodcomms/FloodCommsPackage.kt`
- `App.tsx`
