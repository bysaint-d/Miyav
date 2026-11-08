# Gson maps PostgREST / GoTrue JSON onto private row classes by field name.
-keepclassmembers class az.miyav.data.** {
    <fields>;
}
-keep class az.miyav.data.**$*Row { *; }
-keep class az.miyav.data.**$*Payload { *; }
-keep class az.miyav.data.**$TokenUser { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# TypeToken subclasses must keep their generic signature.
-keep class * extends com.google.gson.reflect.TypeToken

# Strip debug and verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# OkHttp optional platform integrations that are not on Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
