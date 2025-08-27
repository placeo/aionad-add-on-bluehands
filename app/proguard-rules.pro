# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/1113882/Library/Android/sdk/tools/proguard/proguard-android-optimize.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# Suppress warnings and errors about missing classes from libraries
# that are not part of the Android runtime.
-dontwarn java.lang.management.**
-dontwarn javax.servlet.**
-dontwarn io.ktor.**
-dontwarn ch.qos.logback.**

# Keep rules for libausbc (UVC Camera library)

# Disable optimization for this library to prevent JNI issues, but keep shrinking and obfuscation.
# -dontoptimize

-keep,includedescriptorclasses public class com.jiangdg.** { *; }
-keep,includedescriptorclasses public interface com.jiangdg.** { *; }

# Explicitly keep the Size class and its members, which are used by JNI inside nativeGetSupportedSize.
-keep public class com.jiangdg.usb.Size { *; }

# Keep the UvcCameraResolution class that is explicitly reported as not found by JNI.
-keep public class com.vsh.uvc.UvcCameraResolution { *; }

# Keep rules for Ktor and our Server code
# Keep class and member names from being obfuscated.
-keep class io.ktor.** { *; }
-keep class com.skt.photobox.server.** { *; }

# Keep rules for Logback (example, may need more)
-keep class ch.qos.logback.** { *; }
-keepnames class ch.qos.logback.**

# Keep rules for Kotlin serialization
-keepattributes *Annotation*
-keepclassmembers class **.serializer {
    *** Companion;
}
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}
-keep class kotlinx.serialization.** { *; }

# Keep MainActivity and its native methods/fields for JNI
-keep class com.skt.photobox.MainActivity {
    private long native_custom_data;
    private void setMessage(java.lang.String);
    private void onGStreamerInitialized();
    native <methods>;
} 