# 🍃 Hooman's Morphe Patches

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.2.4](https://github.com/arandomhooman/hoomans-morphe-patches/releases/tag/v1.2.4)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details>
<summary>📦 Teach Me Anatomy&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 5.115 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable License Check](#disable-license-check) | Removes the PairIP Google Play license check that otherwise shows a paywall and kills the app on any sideloaded (patched) install. Required for the patched app to launch at all. |  |
| [Unlock Premium](#unlock-premium) | Unlocks Pro — removes the upgrade banners and ads, and unlocks the gated reference articles, quizzes and flashcards — without a subscription. Pro is a local flag so already-synced content works offline; genuinely server-side features still require a real account. |  |

</details>

<details>
<summary>📦 BlockerHero&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Premium](#enable-premium) | Unlocks the premium features (uninstall protection, focus mode, custom blocklists, daily/weekly time limits, block-on-restart, block recent-apps screen, etc.) without a subscription or Google login. |  |

</details>

<details>
<summary>📦 Cronometer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.56.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Gold](#unlock-gold) | Unlocks Cronometer Gold (custom charts, advanced reports, fasting tracker, custom biometrics, diary timestamps & groups, ad-free, and the other Gold gates) without a subscription, by forcing the app's local Gold override on. Gold features run on your own on-device diary data, so they work offline; anything genuinely served from Cronometer's servers still needs a real account. |  |

</details>

<!-- PATCHES_END -->

## 💊 Patch details

### Enable Premium

Forces the app's local premium flag on — unlocking **uninstall protection, focus mode, custom blocklists, daily/weekly time limits, block-on-restart, and the block recent-apps screen**. No subscription or login required. **Target:** BlockerHero `1.5.0`.

### Unlock Premium

Forces Teach Me Anatomy's local **Pro** flag on — removing the upgrade banners and ads, and unlocking the gated **reference articles, the Question Bank (1,900+ quiz questions), flashcards, and 3D models**. No subscription required. Pro is a local flag, so already-synced content works offline; genuinely server-side features still need a real account. **Target:** Teach Me Anatomy `5.115`.

### Disable License Check

Removes the PairIP Google Play license check that Teach Me Anatomy otherwise runs at startup — on any sideloaded (patched) install it shows a Play paywall and kills the app. **Required for the patched app to launch at all**, so keep it enabled. **Target:** Teach Me Anatomy `5.115`.

### Unlock Gold

Forces Cronometer's local **Gold** override on — unlocking custom charts, advanced nutrition reports, the **fasting tracker**, custom biometrics, **diary timestamps & groups**, ad-free, and the other Gold gates. No subscription required. Gold features run on your own on-device diary data, so they work offline; anything genuinely served from Cronometer's servers still needs a real subscription. Cronometer is a Flutter app, so — unlike the others — this patches the native `libapp.so` directly, locating the Gold flag by byte signature rather than a fixed offset. **Target:** Cronometer `4.56.0`.

## 📥 How to install

**Add the patch source** to Morphe Manager once: `https://github.com/arandomhooman/hoomans-morphe-patches` — or use the [deeplink](https://morphe.software/add-source?github=arandomhooman/hoomans-morphe-patches). Then, for the app you want:

### BlockerHero

1. Download [**BlockerHero 1.5.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/blockerhero-1.5.0-universal.apk)
2. In Morphe Manager, **patch it** with the *Enable Premium* patch, then install.

### Teach Me Anatomy

1. Download [**Teach Me Anatomy 5.115 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/teachmeanatomy-5.115-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Premium* **and** *Disable License Check* patches (both enabled by default), then install.
3. Open it and tap **Continue Without Account** (or sign in with your own account) — Pro is unlocked.

### Cronometer

1. Download [**Cronometer 4.56.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/cronometer-4.56.0-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Gold* patch, then install.
3. Open it and sign in with your Cronometer account — the Gold features are unlocked.

> Cronometer ships as split APKs; the link above is a pre-merged universal APK. Gold status shown on the account/subscription screen still reads "free" (that's server-side) — but the gated **features** are unlocked.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
