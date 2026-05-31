# Room - generated code is safe, but keep entities referenced by name in migrations.
-keep class com.impulsive.app.backend.data.local.entity.** { *; }

# Firebase Auth / Google Identity.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Facebook Login.
-keep class com.facebook.** { *; }

-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Device Admin receiver is referenced from the manifest by name.
-keep class com.impulsive.app.security.antibypass.ImpulsiveDeviceAdminReceiver { *; }

# Boot receiver is referenced from the manifest by name.
-keep class com.impulsive.app.backend.service.protection.BootCompletedReceiver { *; }
