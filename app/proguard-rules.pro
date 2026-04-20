# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * {
    public protected *;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
