# Firebase Auth and Google Sign-In
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Credentials Manager
-keep class androidx.credentials.auth.** { *; }

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-dontwarn com.bumptech.glide.**

# WebView
-keepclassmembers class * extends android.webkit.WebViewClient {
    public <methods>;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public <methods>;
}

# General
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
