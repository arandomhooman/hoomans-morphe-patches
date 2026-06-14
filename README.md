# 🍃 Hooman's Morphe Patches

Personal [Morphe](https://morphe.software) patches for paid Android apps.

> Patches are based on the prior work of [ReVanced](https://github.com/ReVanced).

## 🩹 Patches

| App | Patch | Description |
|-----|-------|-------------|
| **BlockerHero** (`com.blockerhero`) | Enable Premium | Unlocks the premium features (uninstall protection, focus mode, custom blocklists, daily/weekly time limits, block-on-restart, block recent-apps screen, …) without a subscription. Target: `1.5.0`. |

## ➕ Add to Morphe

Add `https://github.com/arandomhooman/hoomans-morphe-patches` as a remote source in Morphe Manager, or use the [deeplink](https://morphe.software/add-source?github=arandomhooman/hoomans-morphe-patches).

## 🛠️ Building

```bash
./gradlew buildAndroid
```

Produces a `.mpp` patch bundle under `patches/build/libs/`.

## 📋 License

[GPLv3](LICENSE).
