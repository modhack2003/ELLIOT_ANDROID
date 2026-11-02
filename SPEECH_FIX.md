# ELLIOT 0.6.2 speech callback fix

Fixed and installed on Xiaomi 24066PC95I (ADB DEVICE_SERIAL_REDACTED), 31 October 2026.

The native speech engine looked up invoke(float[]): java.lang.Integer on a
generated Kotlin lambda and aborted when that exact method was unavailable.
NativeTtsCallback is an explicit Java Function1<float[], Integer> adapter with
the exact boxed JNI method descriptor ([F)Ljava/lang/Integer;. It delegates
to the existing streaming implementation, preserving PCM delivery and stop
signals. @Keep protects the callback if shrinking is enabled in a later build.

Validation:
- Gradle preview build, Android test build, and release lint passed.
- Both callback unit tests passed (signature/stop and continuation).
- All three NativeSpeechDeviceTest tests passed on the connected phone:
  actual speech generation and KWS/ASR/VAD processing in the speech process;
  packaged callback signature; worker-death containment and manual recovery.
- APK signature verified; 373 bundled model files matched their hashes.
- Packaged 64-bit native libraries passed the existing 16 KB alignment check.

The old installation was replaced after the user confirmed its data was not
important. Saved data/settings were reset. The APK uses this computer's debug
signing key; the source archive excludes signing keys and build caches.
Version remains 0.6.2 / versionCode 10. Test APK removed after passing tests.
The app was opened for setup. Live acoustic wake-word accuracy and a full
conversational voice session still require user testing.
