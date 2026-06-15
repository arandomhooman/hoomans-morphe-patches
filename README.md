# 🍃 Hooman's Morphe Patches

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.3.0](https://github.com/arandomhooman/hoomans-morphe-patches/releases/tag/v1.3.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;7 patches total
<details>
<summary>📦 Liquid Gallery&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 2.0.14 | 2.1.11 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable License Check](#disable-license-check) | Removes the PairIP Google Play license check that returns NOT_LICENSED on a sideloaded (patched) install and shuts the app down ("Local install check failed due to wrong installer"). Required alongside Unlock Pro for the patched app to launch. |  |
| [Unlock Pro](#unlock-pro) | Unlocks Liquid Gallery Pro (all premium features) without a purchase. Pro is a single local SharedPreferences flag with no server-side gate, so forcing it on unlocks everything the app gates locally. |  |

</details>

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

<details>
<summary>📦 Quizlet&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 10.38.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Plus](#unlock-plus) | Removes ads and unlocks the locally-gated Quizlet Plus features by forcing the account's upgrade type to Plus. Plus status and ad display are decided on-device from a cached flag, so this works without a subscription. Server-side Plus features (AI "Magic Notes"/generation and other cloud/metered tools) are validated and produced on Quizlet's servers and stay locked. |  |

</details>

<!-- PATCHES_END -->

## 💊 Patch details

### Enable Premium

Forces the app's local premium flag on — unlocking **uninstall protection, focus mode, custom blocklists, daily/weekly time limits, block-on-restart, and the block recent-apps screen**. No subscription or login required. **Target:** BlockerHero `1.5.0`.

### Unlock Premium

Forces Teach Me Anatomy's local **Pro** flag on — removing the upgrade banners and ads, and unlocking the gated **reference articles, the Question Bank (1,900+ quiz questions), flashcards, and 3D models**. No subscription required. Pro is a local flag, so already-synced content works offline; genuinely server-side features still need a real account. **Target:** Teach Me Anatomy `5.115`.

### Disable License Check

Removes the PairIP Google Play license check that **Teach Me Anatomy** and **Liquid Gallery** run at startup — on a sideloaded (patched) install it returns NOT_LICENSED and kills the app. **Required alongside the unlock patch so the patched app launches at all**, so keep it enabled. **Targets:** Teach Me Anatomy `5.115`, Liquid Gallery `2.0.14` / `2.1.11`.

### Unlock Gold

Forces Cronometer's local **Gold** override on — unlocking custom charts, advanced nutrition reports, the **fasting tracker**, custom biometrics, **diary timestamps & groups**, ad-free, and the other Gold gates. No subscription required. Gold features run on your own on-device diary data, so they work offline; anything genuinely served from Cronometer's servers still needs a real subscription. Cronometer is a Flutter app, so — unlike the others — this patches the native `libapp.so` directly, locating the Gold flag by byte signature rather than a fixed offset. **Target:** Cronometer `4.56.0`.

### Unlock Pro

Forces Liquid Gallery's local **Pro** flag on — unlocking the locally-gated Pro features (colour customization, batch operations on more than five items, and the other Pro gates) without a purchase. Pro is a single local flag with no server-side check, so forcing it on unlocks everything the app gates locally. Pair it with *Disable License Check* (above). **Target:** Liquid Gallery `2.0.14` / `2.1.11`.

### Unlock Plus

Forces Quizlet's local account tier to **Plus** — removing ads and unlocking the locally-gated Plus surfaces (no upgrade nags, the on-device Plus toggles). Plus status and ad display are decided on-device from a cached flag, so it works without a subscription. The AI / cloud "Plus" features (Magic Notes, generation) are produced on Quizlet's servers and stay locked. You still sign into your own (free) account. **Target:** Quizlet `10.38.1`.

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

### Liquid Gallery

1. Download [**Liquid Gallery 2.1.11 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/liquidgallery-2.1.11-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Pro* **and** *Disable License Check* patches (both enabled by default), then install.
3. Open it — Pro is unlocked.

### Quizlet

1. Download [**Quizlet 10.38.1 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/quizlet-10.38.1-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Plus* patch, then install.
3. Open it and sign in with your Quizlet account — ads are gone and the locally-gated Plus features are unlocked.

> Quizlet's AI / cloud "Plus" features (Magic Notes, generation) are produced server-side and stay locked — this unlocks the ad-free experience and the on-device Plus gates.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
