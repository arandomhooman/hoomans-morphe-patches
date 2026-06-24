# 🍃 Hooman's Morphe Patches

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).
## 🙏 Requesting Patches

**All** requests for patches **must** go under Patch Requests in the **[Discussions Tab](https://github.com/arandomhooman/hoomans-morphe-patches/discussions/new?category=new-patches)**.

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.31.0](https://github.com/arandomhooman/hoomans-morphe-patches/releases/tag/v1.31.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;41 patches total
<details>
<summary>📦 Twitch&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 29.9.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Auto claim channel points](#auto-claim-channel-points) | Claims the channel points bonus chest for you automatically. Whenever Twitch makes a bonus available, the claim fires on its own so you don't have to tap the chest. |  |
| [Block live ads](#block-live-ads) | Routes live streams through a free third-party proxy that strips the ads baked into the stream, so the proxy has to be up for playback to work; turn this patch off if it goes down. Mid-stream ads get dropped reliably, but a single preroll can still slip through when you first open a channel and Twitch has flagged the shared proxy. VOD ads aren't covered. |  |
| [Hide display ads](#hide-display-ads) | Hides the banner, overlay, and in-feed display ads Twitch shows around the app. This doesn't touch the video ads in the stream itself. |  |
| [Show deleted messages](#show-deleted-messages) | Keeps deleted or moderated chat messages readable instead of replacing them with "<message deleted>". The message turns into a tappable spoiler: tap it to reveal the original text. Normally only moderators can do this; the patch grants that access to everyone. |  |

</details>

<details>
<summary>📦 BlockerHero&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Premium](#enable-premium) | Unlocks BlockerHero's premium features without a subscription or Google sign-in, like uninstall protection, focus mode, custom blocklists, daily and weekly time limits, and more. |  |

</details>

<details>
<summary>📦 Tumblr&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**🎯 Supported versions:**

| 45.0.0.109 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable premium UI](#enable-premium-ui) | Turns on the cosmetic Tumblr Premium look: the premium badge and the premium settings entry. This only changes how the app looks on your device. It does not buy or unlock anything on Tumblr's side, so ad-free, the badge on your blog, and anything the server checks still need a real subscription. Use "Remove ads" for an actually ad-free feed. |  |
| [Remove ads](#remove-ads) | Removes the ads in Tumblr: the dashboard ads from the ad networks and the Blaze posts (the paid-promoted posts other people pay to inject into the feed). It leaves your own following feed and reblogs untouched. Premium content like ad-free badges and TumblrMart items are sold by Tumblr's servers and still need a purchase. |  |

</details>

<details>
<summary>📦 Flightradar24&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 11.6.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove ads](#remove-ads) | Removes the ads in Flightradar24. The Gold map layers, flight history, and 3D view are produced on Flightradar24's servers and still need a subscription. |  |
| [Unlock aircraft data](#unlock-aircraft-data) | Shows the squawk code and vertical speed in the flight detail panel instead of the "Unlock feature" lock. Both values are already in the live feed the app receives, so this only stops hiding them. The Gold map layers, flight history, and 3D view come from Flightradar24's servers and still need a subscription. |  |
| [Use your own Maps API key [REQUIRED]](#use-your-own-maps-api-key-required) | Required for the map to load. Patching breaks Flightradar24's built-in Google Maps key, so you have to supply your own from a free Google Cloud project. In this patch's option, create a project, enable Maps SDK for Android, turn on billing, make an API key, and paste it in (leaving it unrestricted is easiest). Remove ads and Unlock aircraft data both pull this in, so the map keeps working with either. | • Google Maps API key |

</details>

<details>
<summary>📦 Moovit&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 5.194.0.1785 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove ads](#remove-ads) | Removes the ads Moovit shows around the map and search and between screens. |  |
| [Unlock Moovit+](#unlock-moovit) | Unlocks the Moovit+ extras without paying, like the extra sort and time-of-travel options and compare-on-map, and stops the upgrade-to-Moovit+ popups that nag you on app open. Things Moovit runs on its servers, like transit ticketing, still need the real subscription. Pair this with Remove ads for the ad-free part of Moovit+. |  |
| [Use your own Maps API key [REQUIRED]](#use-your-own-maps-api-key-required) | Required for the map to load. Patching breaks Moovit's built-in Google Maps key, so you have to supply your own from a free Google Cloud project. In this patch's option, create a project, enable Maps SDK for Android, turn on billing, make an API key, and paste it in (leaving it unrestricted is easiest). Remove ads and Unlock Moovit+ both pull this in, so the map keeps working with either. | • Google Maps API key |

</details>

<details>
<summary>📦 WEBTOON&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.9.5 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove ads](#remove-ads) | Removes the ads WEBTOON shows while reading and browsing: the in-viewer top and end ads, the home feed ads, and the title-page banner. Locked episodes (Fast Pass, Daily Pass, coin-unlocked) are served by WEBTOON's servers and still need coins or a wait, so this does not unlock them. |  |

</details>

<details>
<summary>📦 AGAMA Car Launcher&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 5.0.5 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Full Version](#unlock-full-version) | Unlocks the paid full version of AGAMA Car Launcher, so the pro widgets, theme editor, and the other locked extras open up without buying it. |  |

</details>

<details>
<summary>📦 Cronometer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.56.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Gold](#unlock-gold) | Unlocks Cronometer Gold without a subscription, like custom charts, advanced reports, the fasting tracker, custom biometrics, an ad-free view, and more. Anything Cronometer works out on its own servers still needs Gold. |  |

</details>

<details>
<summary>📦 BandLab&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 11.25.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Membership](#unlock-membership) | Unlocks the BandLab Membership tools that run inside the app without a subscription, like the extra effects and instruments, more tracks, pitch tools, the voice changer, mastering, and more. Anything BandLab makes on its servers, like the stem Splitter, AI video, and distribution, still needs a real membership. |  |

</details>

<details>
<summary>📦 Battery Guru&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.4.8.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock PRO](#unlock-pro) | Unlocks Battery Guru's PRO and removes the ads. PRO is decided on the device, so the deep sleep stats, charge history, themes, and the ad-free view all open up. |  |

</details>

<details>
<summary>📦 Finch&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.73.179 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Plus](#unlock-plus) | Unlocks Finch Plus features without a subscription, including the Plus shop items, extra themes and customization, seasonal event tiers, the monthly recap, and Plus insights. It also clears the upgrade prompts. This is the arm64 build. Cloud backup and cross-device sync run on Finch's own servers and still need the real subscription. Re-signing breaks Google sign-in, so log in with email instead. |  |

</details>

<details>
<summary>📦 Quizlet&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 10.38.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Plus](#unlock-plus) | Removes ads and unlocks the on-device Quizlet Plus features without a subscription. The AI tools, like Magic Notes and generation, run on Quizlet's servers and stay locked. |  |

</details>

<details>
<summary>📦 Alpha Progression&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 6.8.1 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Alpha Progression's premium training tools without a subscription, like the training-plan generator, charts, exercise evaluations, the warmup calculator, and more. They run on the workout data already on your device, so they keep working offline. |  |

</details>

<details>
<summary>📦 AT4K Launcher&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 0.99 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks AT4K Launcher's premium features without paying, like more apps per row and the premium wallpaper options. It all runs on the device, so nothing stays locked. |  |

</details>

<details>
<summary>📦 Collectr&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Collectr's premium features without a subscription, like unlimited collections, price alerts, and the advanced analytics. This is the arm64 build. Anything Collectr serves from its own servers still needs the real subscription. |  |

</details>

<details>
<summary>📦 FolderSync&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.9.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks FolderSync's premium without the in-app purchase and removes the ads, so you get unlimited sync pairs and the advanced options. |  |

</details>

<details>
<summary>📦 Mixel&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.19.11 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Mixel's Premium tier without a subscription: the Pro feature gates open and the premium recipe packs show as entitled. Both gates are read client-side from RevenueCat's entitlements, so this lifts them on-device. Anything Mixel actually checks server-side (if a later build moves a gate there) won't be covered. |  |

</details>

<details>
<summary>📦 Money Manager&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.11.1 GF |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Money Manager's premium and removes the ads: passcode lock, more accounts, custom categories and themes, repeat transactions, advanced stats, and local backup all open. PC/web sync and cloud backup stay tied to the server (it issues an expiry the app checks online), so those keep needing a real subscription. |  |

</details>

<details>
<summary>📦 Projectivy Launcher&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.68 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Projectivy Launcher's premium so the paywall stops sending you to the upgrade screen. Display profiles, custom wallpapers and backgrounds, parental controls, and the locked settings and customization options all open up. The gate is decided on the device, so there is no account to sign in to. |  |

</details>

<details>
<summary>📦 Replaio&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.3.9 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Replaio's premium and removes the ads, so the equalizer, sleep timer, alarm, recording, and favorites all open up. Data Replaio keeps on its servers (synced favorites past the free cap) still needs an account that paid. |  |

</details>

<details>
<summary>📦 Rumble&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.14.5 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Turns on Rumble Premium without a subscription: removes the ad-supported playback, drops the premium upsell, and unlocks the premium-only videos. The premium flag is read from your account profile when you sign in, so it takes effect on a logged-in Rumble account. |  |

</details>

<details>
<summary>📦 Teach Me Anatomy&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 5.115 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Premium](#unlock-premium) | Unlocks Teach Me Anatomy's premium features without a subscription, dropping the upgrade banners and ads and opening up the locked articles, quizzes, and flashcards. Content served from the server still needs a real account. |  |

</details>

<details>
<summary>📦 Adobe Acrobat&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 26.5.0.45958 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks the Acrobat Pro tools that work on your device, like editing text and images and rearranging pages, without paying. The parts Adobe runs on its servers, such as Export to Office, Create PDF, and cloud storage, still need a real subscription. |  |

</details>

<details>
<summary>📦 Cashew&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 5.3.4 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks Cashew's Pro features without a purchase: unlimited budgets and goals, the full past-period budget history, the complete color picker, and no more upgrade popups. Everything here is computed on-device. Google Drive cloud sync is gated separately and won't unlock, and it relies on Google sign-in, which breaks once the app is re-signed. |  |

</details>

<details>
<summary>📦 Essence&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 4.2.8 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks Essence's premium tools without a subscription, like the detailed statistics and insights, custom themes, app lock, data export, and more. They run on your device, so they work offline. The AI recovery coach and anything else Essence makes on its servers still needs the real subscription. |  |

</details>

<details>
<summary>📦 InShot&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.214.1539 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks InShot's Pro features without a subscription: no export watermark, no ads, and the paid filters, effects, transitions, stickers and HD export open. The on-device AI tools (background remover, AI retouch, auto captions) verify the original signing key in native code, so those may not run on a re-signed build; the rest of Pro works offline. |  |

</details>

<details>
<summary>📦 Liquid Gallery&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.0.14 | 2.1.11 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks all of Liquid Gallery's Pro features without a purchase. |  |

</details>

<details>
<summary>📦 Photo Editor Polish&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.763.262 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks the Pro features in Photo Editor Polish without a subscription and drops the ads and upgrade prompts. The AI tools and cloud assets, which the developer runs on its servers, stay locked. |  |

</details>

<details>
<summary>📦 Stash&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.48.2 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Turns on Stash Pro and removes the ads. Pro is a flag the server sends with your account, so the unlock takes effect on a signed-in account once it loads. With it on the ads go away and the Pro-gated UI opens up: the Become Pro upsell card, the home screen block layout, custom collection cover images, and the locked review detail fields. Anything the Stash server checks for a non-paying account is unaffected, so data kept behind a paid account does not sync. Re-signing breaks Google and Facebook login, so sign in with email. |  |

</details>

<details>
<summary>📦 Tracked&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 7.0.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks Tracked's premium training tools without a subscription, like muscle analytics and training programs. They run on the workout data already on your device, so they keep working offline. The separate human-coaching marketplace still needs its own subscription. |  |

</details>

<details>
<summary>📦 Video Converter&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 3.2.2 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Unlocks Video Converter's Pro features without a subscription: 4K output, the full resolution and bitrate range, batch conversion, and watermark removal. Everything runs on the device's own FFmpeg encoder, so the unlocked settings produce real output. This is the arm64 build. The app has no ads, so there is nothing extra to remove. |  |

</details>

<details>
<summary>📦 Wanderlog&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 2.208 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Pro](#unlock-pro) | Lifts Wanderlog's free-tier caps without a subscription: unlimited places per trip, the in-app feature gates unlocked, and no more upgrade nags. Your trips already sync on any tier, so this surfaces them rather than faking data. Features that run on Wanderlog's own servers still need the real subscription and may not work with this alone, including the AI Trip Assistant (the query cap is lifted on-device, but the assistant itself is server-run), offline guide downloads, and live flight-price tracking. |  |

</details>

<details>
<summary>📦 I Am Sober&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 8.6.7 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock Sober Plus](#unlock-sober-plus) | Unlocks the client-side Sober Plus gates backed by the app's local RevenueCat state, including locked-access settings, extra reaction types, skins, workbook and urge logging entries, and the Sober Plus dashboard. Server-hosted services can still require a real account subscription. |  |

</details>

<!-- PATCHES_END -->

## 📥 How to install

The patches apply to the official app, which you supply yourself; this repo doesn't host or redistribute any app. For each app:

1. **Add the patch source** to Morphe Manager once: `https://github.com/arandomhooman/hoomans-morphe-patches`, or use the [deeplink](https://morphe.software/add-source?github=arandomhooman/hoomans-morphe-patches).
2. **Get the app's APK** at the exact version in the table above: export it from the Play Store, or download it from APKMirror / APKCombo. Split bundles (`.apks` / `.xapk`) are fine; Morphe Manager merges them automatically (or merge with APKEditor first).
3. **Patch and install** in Morphe Manager with the patch(es) you want. Required companion patches (PairIP / license-check / signature bypasses) are pulled in automatically.

> Patching re-signs the app, so Google sign-in stops working on patched builds; log in with email or username where the app allows it. Official subscription/account status is decided server-side and often still reads "free", so the patches unlock the on-device features, not anything the app computes or streams from its servers.

### App-specific notes

- **Moovit** and **Flightradar24**: re-signing invalidates the built-in Google Maps key, so the map goes blank. The *Use your own Maps API key [REQUIRED]* patch takes a key you make for free in the [Google Cloud Console](https://console.cloud.google.com/): create a project, enable **Maps SDK for Android**, turn on **billing** (the Maps SDK requires it; normal use stays in the free monthly credit, and skipping it is the most common cause of a blank map), create an API key under **Credentials**, leave it unrestricted, and paste it into the patch's option.
- **Twitch**: *Block live ads* reroutes live streams through the `eu.luminous.dev` proxy, so it depends on that proxy staying up; if live streams stop loading, remove that patch. VOD in-stream video ads aren't covered.
- **Collectr**: Flutter arm64 build; patch it on an arm64 device.
- **PairIP apps** (Teach Me Anatomy, Liquid Gallery, Essence, BandLab, Photo Editor Polish, AT4K): the license-check / signature bypass that lets the re-signed build launch is applied automatically; most let you skip making an account ("Continue without account" / "Do this later").
- **Server-gated extras stay locked**: Acrobat's cloud tools (Export/Create/Combine/OCR), Photo Editor Polish's AI tools, Quizlet's Magic Notes, WEBTOON's locked episodes, BandLab's stem Splitter, and the like are computed or served by each app's backend, so the patches don't touch them.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
