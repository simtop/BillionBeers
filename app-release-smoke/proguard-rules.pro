# Keep the smoke entry point discoverable without changing the target app's R8 behavior.
-keep class com.simtop.billionbeers.releasesmoke.ReleaseLaunchSmokeTest { *; }
-dontwarn com.google.errorprone.annotations.**
