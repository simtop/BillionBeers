# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class * extends androidx.fragment.app.Fragment{}
-keepnames class * extends android.os.Parcelable

# The release smoke path loads the application by manifest name and exercises the Metro graph through
# the shared instrumentation class loader. Keep the runtime contracts that cross that boundary.
-keep class com.simtop.billionbeers.BillionBeersApplication { *; }
-keep interface com.simtop.core.di.GraphProvider { *; }
-keep class com.simtop.** { *; }
-keep class dev.zacsweers.metro.** { *; }
-keep class dev.zacsweers.metrox.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.** { *; }
-keep class com.google.android.play.core.** { *; }
