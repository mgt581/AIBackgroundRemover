# Firebase Rules
-keep class com.google.firebase.** { *; }
-keepattributes *Annotation*

# JavaScript Interface (Crucial for WebView bridge)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Credentials & Auth
-keep class androidx.credentials.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-dontwarn com.bumptech.glide.**

# ML Kit (Fixes R8 issues with segmentation-selfie)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_segmentation_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }

# General
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
