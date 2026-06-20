# 🍃 Hooman's Morphe Patches

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).
## 🙏 Requesting Patches

**All** requests for patches **must** go under Patch Requests in the **[Discussions Tab](https://github.com/arandomhooman/hoomans-morphe-patches/discussions/new?category=new-patches)**.

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.14.1](https://github.com/arandomhooman/hoomans-morphe-patches/releases/tag/v1.14.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;15 patches total
<details>
<summary>📦 Twitch&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 29.9.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Block live ads](#block-live-ads) | Removes the ads stitched into live streams (SureStream) by routing the HLS manifest request through the Luminous ad-block proxy (eu.luminous.dev) instead of Twitch's own usher server. The proxy fetches a clean manifest, so the stream comes back without the server-inserted ad segments. It relies on that third-party proxy staying up; if it goes down, live streams stop loading until you remove the patch. This covers the stitched live-stream ads only; VOD ads are not touched. |  |
| [Hide display ads](#hide-display-ads) | Hides the banner, overlay, and in-feed display ads Twitch renders around the app (not the video ads in the stream itself). Every one of those ads is fetched from Twitch's ad edge and runs through a single parser before anything is drawn; forcing that parser to report no ad means none of them render. The decision is on-device, so this needs no account change and leaves the rest of the app alone. |  |

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
<summary>📦 AGAMA Car Launcher&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 5.0.5 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Full Version](#unlock-full-version) | Unlocks AGAMA Car Launcher's paid full version without buying it, so the pro widgets, theme editor and other locked extras all open up. Full versus trial is just a stored flag that the Google Play purchase writes and never reads back, so forcing it on works without paying and survives reboots. The launcher runs everything on-device with no server entitlement, so nothing stays locked. |  |

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
<summary>📦 BandLab&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 11.25.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Membership](#unlock-membership) | Unlocks the BandLab Membership tools that run on the device. The app decides membership from one cached status object read through a single repository, so forcing the member flag and the per-feature check turns on the in-Studio tools a subscription would give. That covers the extra effects and instruments, the larger track count, pitch tools like AutoPitch and manual correction, the voice changer, mastering EQ and presets, comping, and audio-to-MIDI. Anything BandLab renders on its servers (stem Splitter, AI video, distribution, members-only beats) still checks the account server-side and stays locked. |  |

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

<details>
<summary>📦 Alpha Progression&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 6.8.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Forces Alpha Progression's local Pro flag on, unlocking the premium training tools — the training-plan generator, charts, exercise evaluations, warmup calculator, progression recommendations, deload, periodisation and RIR tracking — without a subscription. These run on the workout data already on your device, so they keep working offline. Your account's official subscription status is validated by RevenueCat and is unaffected. |  |

</details>

<details>
<summary>📦 Collectr&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Collectr's client-gated premium features, like unlimited collections, price alerts, and the advanced analytics, by forcing the local membership tier to pro so the on-device checks treat the account as premium. This is the arm64 build. Anything Collectr's servers authorize or serve on their own (account-bound data the backend gates) is not granted by a local flag. |  |

</details>

<details>
<summary>📦 Teach Me Anatomy&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 5.115 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Teach Me Anatomy's Pro features without a subscription: no upgrade banners or ads, plus the gated reference articles, quizzes, and flashcards. Content you have already synced works offline. Features served from the server still need a real account. |  |

</details>

<details>
<summary>📦 Adobe Acrobat&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 26.5.0.45958 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks the Acrobat Pro features that run on-device behind the cached subscription status: editing text and images, and organizing/rearranging pages. Acrobat keeps every premium gate behind one services account that reports entitlements from a locally cached flag, so reporting the on-device Pro entitlements active opens those tools without paying. Features the server runs or stores, like Export/Convert to Office, Create PDF, and Document Cloud storage, are authorized on Adobe's side and stay locked. |  |

</details>

<details>
<summary>📦 Essence&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.2.8 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Forces Essence's local Pro flag on, unlocking the premium tools that run on the device without a subscription: the detailed statistics and insights, the custom color themes, app lock, data export, and the other locally-gated premium features. The flag is read from one cached status, so it works offline. The AI recovery coach and anything else Essence generates or serves from its backend still checks the account and stays locked. |  |

</details>

<details>
<summary>📦 Liquid Gallery&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.0.14 | 2.1.11 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks Liquid Gallery Pro without a purchase. Pro is a single local flag with no server-side check, so switching it on enables the Pro features the app gates on device. |  |

</details>

<details>
<summary>📦 Photo Editor Polish&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.763.262 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks the Pro features in Photo Editor Polish without a subscription. Pro is decided on-device from one cached flag that every premium gate reads, so forcing it on enables the locally-gated tools and drops the ads and upgrade prompts. Content generated or validated on the developer's servers (AI tools, cloud assets) stays locked. |  |

</details>

<details>
<summary>📦 Tracked&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 7.0.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Forces Tracked's local Pro flag on, unlocking the premium training tools it gates on device, like muscle analytics and training programs, without a subscription. These run on the workout data already on your device, so they keep working offline. Your account's official subscription status is validated by RevenueCat and is unaffected, and the separate human-coaching marketplace still needs its own subscription. |  |

</details>

<!-- PATCHES_END -->

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

### Alpha Progression

1. Download [**Alpha Progression 6.8.1 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/alphaprogression-6.8.1-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Premium* patch, then install.
3. Open it. The Pro training tools (plan generator, charts, deload, periodisation, RIR) are unlocked.

> Alpha Progression is split APKs; the link above is a pre-merged universal APK. Your subscription status stays "free" because that is server-side, but the on-device Pro features are unlocked.

### Photo Editor Polish

1. Download [**Photo Editor Polish 1.763.262 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/photoeditorpolish-1.763.262-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Pro* patch, then install. The app's native and DEX signature checks are bypassed automatically, so the patched build runs.
3. Open it. Pro is unlocked and the ads are gone.

> Photo Editor Polish is split APKs; the link above is a pre-merged universal APK. The AI tools (AI Remove, AI Expand, Enhancer) run on the developer's servers and stay locked.

### BandLab

1. Download [**BandLab 11.25.3 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/bandlab-11.25.3-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Membership* patch, then install. The install-check bypass that lets the re-signed build launch is applied automatically.
3. Open it and sign in with your own BandLab account. The Membership Studio tools (extra effects and instruments, the larger track count, AutoPitch and manual pitch correction, the voice changer, mastering EQ and presets, comping, audio-to-MIDI, the smart tools) are unlocked, and the ads are gone.

> BandLab is split APKs; the link above is a pre-merged universal APK. Anything BandLab renders or validates on its servers (members-only beats, the stem Splitter, distribution, AI video) stays locked. On a de-Googled device login can fail because BandLab's server requires Google Play Integrity; on a normal phone with Play Services it signs in fine.

### Essence: Quit Addiction

1. Download [**Essence 4.2.8 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/essence-4.2.8-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Pro* patch, then install. The PairIP license-check bypass that lets the re-signed build launch is applied automatically.
3. Open it. During the signup flow you can tap **Continue without account**. The Pro tools (detailed statistics and insights, custom color themes, app lock, data export, and the other on-device premium features) are unlocked.

> Essence is split APKs and ships an arm64 build; the link above is a pre-merged universal APK. The AI recovery coach and anything else Essence generates or serves from its backend still checks the account and stays locked.

### Tracked

1. Download [**Tracked 7.0.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/tracked-7.0.0-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Pro* patch, then install.
3. Open it. During onboarding you can tap **Do this later** to skip making an account. The Pro tools (muscle analytics, training programs, and the other on-device premium features) are unlocked.

> Tracked is split APKs and ships an arm64 build; the link above is a pre-merged universal APK. Your account's official subscription status is server-side and stays unchanged, and the separate human-coaching marketplace still needs its own subscription.

### AGAMA Car Launcher

1. Download [**AGAMA Car Launcher 5.0.5 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/carlauncher-5.0.5-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Full Version* patch, then install.
3. Open it. It runs as the licensed full version, so the pro widgets, theme editor, and the other paid extras are unlocked and the trial prompts are gone.

> AGAMA Car Launcher is split APKs; the link above is a pre-merged universal APK. The launcher keeps everything on the device with no account, so the whole paid feature set is unlocked.

### Collectr

1. Download [**Collectr 2.5.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/collectr-2.5.0-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Premium* patch, then install.
3. Open it. The on-device premium features (unlimited collections, price alerts, advanced analytics, and the other locally-gated extras) are unlocked.

> Collectr is split APKs and ships a Flutter arm64 build; the link above is a pre-merged universal APK, so patch it on an arm64 device. Anything Collectr's backend gates or serves for your account is server-side and stays unchanged.

### Adobe Acrobat

1. Download [**Adobe Acrobat 26.5.0.45958 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/acrobat-26.5.0.45958-universal.apk)
2. In Morphe Manager, **patch it** with the *Unlock Pro* patch, then install.
3. Open it. The on-device Pro tools (edit text and images, organize and rearrange pages, crop) are unlocked and the subscribe prompts on them are gone.

> Adobe Acrobat is split APKs; the link above is a pre-merged universal APK. Only the tools that run on the device are unlocked. Anything Acrobat does on its servers (Export and Convert to Office, Create PDF, Combine, Compress, OCR, password, Document Cloud) is authorized server-side and stays locked, so those keep their crown.

### Twitch

1. Download [**Twitch 29.9.1 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/twitch-29.9.1-universal.apk)
2. In Morphe Manager, **patch it** with the *Block live ads* and *Hide display ads* patches, then install.
3. Open it and sign in with your Twitch account. Live streams play without the stitched ad breaks, and the banner and in-feed display ads are gone.

> Twitch is split APKs; the link above is a pre-merged universal APK. *Block live ads* reroutes live streams through the eu.luminous.dev proxy, so it depends on that proxy staying up; if it goes down, remove that patch and live streams load normally again. Between them the two patches clear the stitched live-stream ads and the banner, overlay, and in-feed display ads. The in-stream video ads on VODs are not covered.

### Moovit

Moovit needs an extra step the other apps don't. Its built-in Google Maps key is locked to Moovit's signing certificate, and patching re-signs the app, so Google stops accepting that key and the map goes blank. You have to supply a Maps key from your own Google Cloud project. It's free to set up and the patch won't apply without one.

**Get a Google Maps API key first (free):**

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) and create a project (or pick an existing one).
2. Open **APIs & Services > Library**, search for **Maps SDK for Android**, and enable it.
3. Turn on **billing** for the project (the **Billing** section, link a billing account). Google requires one for the Maps SDK, but normal use stays inside the free monthly credit. Skipping this is the most common reason the map comes up blank.
4. Open **APIs & Services > Credentials**, click **Create credentials > API key**, and copy the key.
5. Leave the key **unrestricted** (simplest, and the right choice if you patch with Morphe Manager, since the cert it signs with isn't fixed). To lock it down instead, set **Application restrictions** to **Android apps** and add package `com.tranzmate` with the SHA-1 of the certificate your patched build is signed with (read it from the patched APK with `apksigner verify --print-certs`).

**Then patch:**

1. Download [**Moovit 5.194.0.1785 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/moovit-5.194.0.1785-universal.apk)
2. In Morphe Manager, select the *Remove ads* and *Unlock Moovit+* patches. Both pull in the *Use your own Maps API key* patch, which is what keeps the map working. Open its option, paste your key into **Google Maps API key**, then patch and install.
3. Open it. The ads are gone, the on-device Moovit+ extras are unlocked, and the map loads on your own key.

> Moovit is split APKs; the link above is a pre-merged universal APK. If the map is blank after patching, the key is empty or wrong, restricted to a different certificate, or its project is missing **Maps SDK for Android** or billing. The Moovit+ subscription is still checked by Moovit's backend, so server-side features like transit ticketing stay locked.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
