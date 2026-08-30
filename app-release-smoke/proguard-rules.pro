# AGP requires the tested application's shrinking contract to match the test APK.
# Keep the smoke entry point discoverable while leaving the target app's R8 mapping authoritative.
-dontobfuscate
-keep class com.simtop.billionbeers.ReleaseGraphSmokeTest { *; }
-dontwarn com.google.errorprone.annotations.**
