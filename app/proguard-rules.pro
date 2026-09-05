# GeckoView JNI and Reflection
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.geckoview.**
-dontwarn org.mozilla.gecko.**

# Keep Parcelable implementations for Android IPC and state restoration
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep JNI native bindings
-keepclasseswithmembernames class * {
    native <methods>;
}
