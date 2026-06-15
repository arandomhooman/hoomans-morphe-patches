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
| [Unlock Pro](#unlock-pro) | Unlocks Liquid Gallery Pro without a purchase. Pro is a single local flag with no server-side check, so switching it on enables the Pro features the app gates on device. |  |

</details>

<details>
<summary>📦 Teach Me Anatomy&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 5.115 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable License Check](#disable-license-check) | Removes the PairIP Google Play license check, which shows a paywall and kills the app on a sideloaded (patched) install. Keep it enabled so the patched app can launch. |  |
| [Unlock Premium](#unlock-premium) | Unlocks Teach Me Anatomy's Pro features without a subscription: no upgrade banners or ads, plus the gated reference articles, quizzes, and flashcards. Content you have already synced works offline. Features served from the server still need a real account. |  |

</details>

<details>
<summary>📦 BlockerHero&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Premium](#enable-premium) | Unlocks BlockerHero's premium features without a subscription or Google sign-in: uninstall protection, focus mode, custom blocklists, daily and weekly time limits, block-on-restart, and blocking the recent-apps screen. |  |

</details>

<details>
<summary>📦 Cronometer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.56.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Gold](#unlock-gold) | Forces Cronometer's local Gold flag on, unlocking custom charts, advanced reports, the fasting tracker, custom biometrics, diary timestamps and groups, and an ad-free view without a subscription. These features run on the diary data already on your device, so they keep working offline. Anything Cronometer computes on its own servers still needs a subscription. |  |

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

Unlocks BlockerHero's premium features without a subscription or Google sign-in: uninstall protection, focus mode, custom blocklists, daily and weekly time limits, block-on-restart, and blocking the recent-apps screen. The app reads premium from one local flag, so setting it covers all of them. Target: BlockerHero `1.5.0`.

### Unlock Premium

Switches on Teach Me Anatomy's local Pro flag. That removes the upgrade banners and ads and unlocks the gated reference articles, the Question Bank (1,900+ quiz questions), flashcards, and 3D models. Content you have already synced keeps working offline; anything served fresh from the server still needs a real account. It also applies the PairIP license-check bypass automatically, which a sideloaded build needs in order to launch. Target: Teach Me Anatomy `5.115`.

### Unlock Gold

Forces Cronometer's local Gold override on, which unlocks custom charts, advanced nutrition reports, the fasting tracker, custom biometrics, diary timestamps and groups, and the ad-free view. These features run on the diary data already on your device, so they work offline. Anything Cronometer computes on its own servers still needs a subscription. Because Cronometer is a Flutter app, this patch edits the native `libapp.so` instead of the DEX, finding the Gold flag by byte signature rather than a fixed offset. Target: Cronometer `4.56.0`.

### Unlock Pro

Turns on Liquid Gallery's local Pro flag, unlocking the Pro features the app gates on device, such as colour customization and batch operations on more than five items. There is no server-side check, so the flag alone is enough. It also applies the PairIP license-check bypass automatically, which a sideloaded build needs in order to launch. Target: Liquid Gallery `2.0.14` / `2.1.11`.

### Unlock Plus

Sets Quizlet's local account tier to Plus. Ads disappear and the on-device Plus features unlock, including the upgrade prompts and the local Plus toggles, because that status is read from a cached flag. The AI and cloud features (Magic Notes, generation) run on Quizlet's servers and stay locked. You still sign into your own free account. Target: Quizlet `10.38.1`.

## 📥 How to install

**Add the patch source** to Morphe Manager once: `https://github.com/arandomhooman/hoomans-morphe-patches`, or use the [deeplink](https://morphe.software/add-source?github=arandomhooman/hoomans-morphe-patches). Then, for the app you want:

### BlockerHero

1. Download [**BlockerHero 1.5.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/blockerhero-1.5.0-universal.apk)
2. In Morphe Manager, **patch it** with the *Enable Premium* patch, then install.

### Teach Me Anatomy

1. Download [**Teach Me Anatomy 5.115 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/teachmeanatomy-5.115-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Premium* patch, then install. The required PairIP license-check bypass is applied automatically.
3. Open it and tap **Continue Without Account** (or sign in with your own account). Pro is unlocked.

### Cronometer

1. Download [**Cronometer 4.56.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/cronometer-4.56.0-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Gold* patch, then install.
3. Open it and sign in with your Cronometer account. The Gold features are unlocked.

> Cronometer ships as split APKs; the link above is a pre-merged universal APK. Gold status on the account screen still reads "free" because that is server-side, but the gated features are unlocked.

### Liquid Gallery

1. Download [**Liquid Gallery 2.1.11 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/liquidgallery-2.1.11-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Pro* patch, then install. The required PairIP license-check bypass is applied automatically.
3. Open it. Pro is unlocked.

### Quizlet

1. Download [**Quizlet 10.38.1 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/quizlet-10.38.1-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Plus* patch, then install.
3. Open it and sign in with your Quizlet account. Ads are gone and the locally-gated Plus features are unlocked.

> Quizlet's AI and cloud features (Magic Notes, generation) are produced server-side and stay locked. This patch covers the ad-free experience and the on-device Plus features.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
