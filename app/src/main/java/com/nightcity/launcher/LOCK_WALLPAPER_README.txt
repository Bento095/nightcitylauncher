NightCity Theme Lock Wallpaper integration

Changed files:
- LockWallpaper.kt (new): generates a theme-colored, geometric split wallpaper and applies it to the LOCK screen only.
- NcApp.kt: a LaunchedEffect keyed to palKey/customPals automatically refreshes the lock wallpaper when the selected theme changes or custom palettes are saved.

Required AndroidManifest.xml permission (manifest was not included in the supplied source ZIP):
<uses-permission android:name="android.permission.SET_WALLPAPER" />

This calls WallpaperManager.FLAG_LOCK, so it does not intentionally replace the Home wallpaper.
The pattern is generated locally from the active NcPalette colors: y (accent panel), c (divider/tech details), r (hazard accents), bg/bg2 (dark base).
