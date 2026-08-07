# Retrofit does GSON-based stubbing for type variables.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Gson
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep retrofit models
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.shubh.song.data.api.dto.** { *; }

# Room
-keep class * extends android.arch.persistence.room.RoomDatabase
-keep @android.arch.persistence.room.Entity class *

# OkHttp
-keepattributes SourceFile, LineNumberTable
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Media3
-dontwarn androidx.media3.**