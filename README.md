# 🍃 Hooman's Morphe Patches

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).

## 🩹 Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/arandomhooman/hoomans-morphe-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 BlockerHero&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 1.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Enable Premium](#enable-premium) | Unlocks the premium features (uninstall protection, focus mode, custom blocklists, daily/weekly time limits, block-on-restart, block recent-apps screen, etc.) without a subscription. |  |

</details>

<!-- PATCHES_END -->

## 💊 Patch details

### Enable Premium

Forces the app's local premium flag on — unlocking **uninstall protection, focus mode, custom blocklists, daily/weekly time limits, block-on-restart, and the block recent-apps screen**. No subscription or login required. **Target:** BlockerHero `1.5.0`.

## 📥 How to install

1. **Download the app** — [**BlockerHero 1.5.0 (universal APK)**](https://github.com/arandomhooman/hoomans-morphe-patches/releases/download/v1.0.0/blockerhero-1.5.0-universal.apk) — a single, ready-to-patch APK (no split/XAPK to merge).
2. **Add this patch source** to Morphe Manager: `https://github.com/arandomhooman/hoomans-morphe-patches` — or use the [deeplink](https://morphe.software/add-source?github=arandomhooman/hoomans-morphe-patches).
3. In Morphe Manager, **patch the downloaded APK** with the *Enable Premium* patch, then install it.

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
