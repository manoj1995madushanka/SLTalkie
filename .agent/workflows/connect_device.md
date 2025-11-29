---
description: How to connect an Android device for testing
---

# Connect Android Device

## 1. Add ADB to PATH
Your computer doesn't know where the `adb` command is. Run this command in your terminal to add it:

```bash
export PATH=$PATH:/Users/manojmadushanka/Library/Android/sdk/platform-tools
```

To make this permanent, add it to your shell profile (`~/.zshrc`):
```bash
echo 'export PATH=$PATH:/Users/manojmadushanka/Library/Android/sdk/platform-tools' >> ~/.zshrc
source ~/.zshrc
```

## 2. Enable Developer Options on Phone
1.  Go to **Settings** > **About Phone**.
2.  Tap **Build Number** 7 times until it says "You are a developer".

## 3. Enable USB Debugging
1.  Go to **Settings** > **System** > **Developer Options**.
2.  Scroll down and enable **USB Debugging**.

## 4. Connect and Authorize
1.  Connect your phone to your Mac via USB.
2.  On your phone, a popup will appear: "Allow USB debugging?".
3.  Check "Always allow from this computer" and tap **Allow**.

## 5. Verify Connection
Run this command to see if your device is recognized:
```bash
adb devices
```
It should show a device ID and `device` (not `unauthorized`).

## 6. Run the App
Now you can run the app:
```bash
npx react-native run-android
```
