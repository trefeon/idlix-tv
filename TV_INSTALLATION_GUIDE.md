# 📺 Installing IDLIX on Android TV & Google TV (For Non-Technical Users)

This guide is for anyone who wants to install the IDLIX app on a Smart TV, Android TV Box, or Google TV — no technical skills, laptop, or coding required.

---

## 📋 Requirements

Before you start, prepare the following:
1. **TV device**: Android TV, Google TV, or a TV Box (e.g. Mi TV Stick, Chromecast with Google TV, Xiaomi Box, Realme TV Stick, Aqua, Coocaa, TCL, etc.).
2. **APK file**: the IDLIX TV app file (`idlix-tv-v*.apk`), downloaded from the [releases page](https://github.com/trefeon/idlix-tv/releases).
3. **Wi-Fi**: make sure the TV is connected to your home Wi-Fi network.

---

## ⚙️ Required Step: Allow "Unknown Sources"

Android TV blocks app installs from outside the Google Play Store by default. Follow these steps once:

### For Google TV (newer versions / Chromecast / Sony / TCL):
1. Open **Settings** in the top-right corner of the TV screen (gear icon).
2. Go to **System** > **About**.
3. Scroll down to **Android TV OS Build**.
4. Press the **OK / center** button on the remote **7 times in a row** until you see *"You are now a developer!"*.
5. Press *Back*, then open **Apps** > **Security & Restrictions**.
6. Select **Install unknown apps**.
7. Find the file manager app you will use (e.g. *File Commander*, *Send Files to TV*, or *AnExplorer*) and set it to **Allow**.

### For standard Android TV (Xiaomi Mi TV Stick / Mi Box):
1. Open **Settings** > **Device Preferences**.
2. Select **Security & Restrictions**.
3. Enable **Unknown Sources** for your file manager app.

---

## 🚀 Pick an Install Method (Choose One)

There are 3 easy methods — pick whichever fits your TV:

### 🔹 Method 1: Using Your Phone (Most Practical, No Cables / USB)

*Highly recommended for Mi TV Stick or Chromecast, which have no USB port.*

1. **Install a transfer app on your phone & TV**:
   * Open the **Google Play Store** on both your TV and your Android phone.
   * Search for and install **"Send Files to TV" (SFTTV)** on both devices.
   * Also install a file manager on the TV (e.g. **File Commander** or **AnExplorer** from the TV Play Store).
2. **Send the APK from your phone to the TV**:
   * Make sure your phone and TV are on the **same Wi-Fi network**.
   * Open *Send Files to TV* on the TV and select **RECEIVE**.
   * Open *Send Files to TV* on your phone, select **SEND**, then pick the IDLIX APK file on your phone.
   * Select your TV's name on the phone screen. The transfer takes a few seconds.
3. **Install on the TV**:
   * When finished, tap the file directly in *Send Files to TV* on the TV, then choose **Open with Package Installer**.
   * If it won't open directly, open **File Commander / AnExplorer** on the TV, go to the **Download** folder, and tap the IDLIX APK to install it.
   * Select **Install**. Done!

---

### Method 2: Using a Flash Drive / USB Stick (Easiest If You Have a USB Port)

*Best for Smart TVs or TV Boxes with a regular USB port.*

1. **Prepare the flash drive**:
   * Copy the IDLIX APK from your computer/laptop onto the flash drive.
2. **Install a file manager on the TV**:
   * Open the **Google Play Store** on the TV.
   * Search for and install a free file manager (e.g. **File Commander**, **FX File Explorer**, or **AnExplorer**).
3. **Plug the flash drive into the TV**:
   * Insert the flash drive into the TV's USB port.
4. **Open and install**:
   * Open the file manager app on the TV.
   * Open your flash drive, find the IDLIX APK file.
   * Press **OK** on the APK file, then select **Install**.
   * Wait until you see *"App installed"*.

---

### 🔹 Method 3: Using the "Downloader" App (Download Directly on the TV)

1. Open the **Google Play Store** on the TV, search for and install **Downloader by AFTVnews**.
2. Open **Downloader** on the TV.
3. Enter the direct download link of the APK in the URL field (get it from the [releases page](https://github.com/trefeon/idlix-tv/releases)).
4. Press **Go**. The file downloads straight to the TV.
5. When the download finishes, the install window appears automatically. Select **Install**.

---

## ❓ Frequently Asked Questions & Troubleshooting

### 1. Getting a "Blocked by Play Protect" warning?
* **Cause**: the app is sideloaded manually, from outside the official Play Store.
* **Fix**: tap **"More details"**, then select **"Install anyway"**. The app is 100% safe and free of viruses/ads.

### 2. "Connection failed" status in the TV Settings connection test?
* **Check 1**: make sure the TV is actually online (open YouTube or the Play Store to confirm the Wi-Fi works, not just that it's connected to the router).
* **Check 2**: IDLIX mirrors change address from time to time — fully close the IDLIX app and reopen it so it re-resolves the latest address from `https://idlix.net/`, then retry the connection test.
* **Check 3**: if it still fails, update to the latest release from the [releases page](https://github.com/trefeon/idlix-tv/releases) — old versions may point at a dead mirror.
* **Check 4**: if no device in the house can open streaming sites, your ISP's network is likely the problem — try a phone hotspot as a comparison.

### 3. Subtitles missing, or want to change the language?
* While a video is playing, press the **Up/Down** buttons on the TV remote to bring up the player controls.
* Select the **Subtitle (CC) icon** to pick a built-in IDLIX subtitle or search for alternatives via the SubDL integration.
