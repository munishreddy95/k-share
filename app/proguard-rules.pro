# ZXing embedded launches scanner components by intent; keep them stable in release builds.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
