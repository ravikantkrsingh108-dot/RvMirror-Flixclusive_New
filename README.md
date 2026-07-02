# RvMirror Flixclusive Provider

Kotlin/Gradle provider repo for Flixclusive, based on the `flixclusiveorg/flx-providers` layout and the working RvMirror CloudStream implementation.

## What it does

- Exposes Netflix, Prime Video, and Hotstar mirror catalogs.
- Searches the RvMirror/netfree mobile endpoints.
- Loads movie and TV metadata, including seasons and episodes.
- Resolves playable HLS links through the NewTV player endpoint.
- Saves discovered title IDs from home pages, searches, and metadata suggestions in Android preferences, then merges those IDs back into catalog results so the catalog grows as the provider sees more content.

## Build

Use a local Gradle installation or add a Gradle wrapper, then run:

```powershell
gradle generatePlugins
```

The repo manifest is in `repo.json`, and the provider module is in `providers/RvMirror`.
