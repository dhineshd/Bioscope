# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/rohitraghunathan/Projects/adt-bundle-mac-x86_64-20140702/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

 #-keepclassmembers class fqcn.of.javascript.interface.for.webview {
 #   public *;
 #}

# Keep the logging when built with proguard
-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*

# Dont warn us about the LoginAuthenticator and SMTPAppender missing dependencies as we dont use those features
-dontwarn ch.qos.logback.core.net.*

# Dont warn about 7zip, LZMA, xz since we dont use those compressions
-dontwarn org.apache.commons.compress.archivers.sevenz.*
-dontwarn org.apache.commons.compress.compressors.lzma.*
-dontwarn org.apache.commons.compress.compressors.xz.*

# Dont warn about missing javax.naming for SpongyCastle, this doesnt appear to be used
-keep class org.spongycastle.**
-dontwarn org.spongycastle.jce.provider.X509LDAPCertStoreSpi
-dontwarn org.spongycastle.x509.util.LDAPStoreHelper
-dontwarn org.spongycastle.cert.dane.fetcher.*

#
# Seems to be caused by an erroneous dependency from Google analytics
# http://stackoverflow.com/questions/32974025/com-google-android-gms-internal-zzhu-cant-find-referenced-class-android-securi
-dontwarn com.google.android.gms.internal.zzhu

-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Demach GSON files
-keep class com.google.gson.demach.** {
    <fields>;
    <methods>;
}

# Demach model
-keep class com.demach.** {
    <fields>;
    <methods>;
}

# Application classes that will be serialized/deserialized over Gson
-keep class com.trioscope.chameleon.types.** { *; }
-keepnames class org.spongycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
-keepclassmembers class org.spongycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey {
    private BigInteger modulus;
    private BigInteger publicExponent;
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


