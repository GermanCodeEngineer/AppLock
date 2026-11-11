# Authentication and recovery

AppLock supports PINs, passwords, patterns, and authenticator codes as primary unlock methods. Optional biometrics remain available alongside the selected method.

On a new installation, create a recovery password before selecting an unlock method. Existing installations request one after successful authentication. Recovery passwords require 12–128 characters and confirmation; their salted PBKDF2-SHA256 hashes are stored separately from the primary credentials.

If an existing installation has no recovery password and you forgot its primary credential, choose **Forgot password?** on AppLock's own unlock screen, then **Reset using device screen lock**. Android must verify your device PIN, pattern, or password before AppLock resets authentication. Cancellation leaves authentication intact. Successful verification requires recovery-password enrollment followed by a new primary method, while keeping protected apps and settings. This migration fallback is unavailable once a recovery password has been configured. A device without available screen-lock verification cannot use this fallback.

Select **Use authenticator** during setup or **Set up authenticator** in security settings. Scan the QR code with an authenticator, or enter its Base32 secret manually. Confirm a six-digit code before activation. Enrollment uses SHA-1 and a 30-second period, with one adjacent time step allowed for clock differences. The secret is encrypted using an AES-GCM key in Android Keystore. Canceling enrollment keeps the current method active.

A successfully used code cannot unlock another surface again. Wait for the next authenticator code when unlocking multiple apps. Five unsuccessful attempts impose a 30-second delay.

**Use recovery password** is available only on AppLock's own unlock screen. It is absent from protected-app overlays and the admin-disable screen. Recovery clears the PIN/password, pattern, authenticator secret, biometric setting, and temporary unlock sessions. It keeps protected apps, anti-uninstall settings, other preferences, and the recovery password, then requires primary authentication setup again. A missing or unavailable Keystore key never opens unauthenticated setup; use recovery instead.

## Validation

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` for the JVM tests and debug build. Tests include RFC 4226/6238 vectors, clock tolerance, strict code input, replay rejection, an independently generated PBKDF2 vector, recovery reset behavior, and startup routing for new, existing, and TOTP-only installations.

The device test `AuthenticationDeviceTest` exercises real Android Keystore encryption, code consumption across repository instances, and recovery. Run `./gradlew -PisolatedAuthenticationTest=true :app:connectedDebugAndroidTest` to install a separate temporary `.authtest` application. It deliberately skips applications whose ID does not end in `.authtest`, because it changes authentication settings. Uninstall the test applications after validation.

Manual UI checks:

- New installation: recovery setup precedes primary setup; mismatching or short recovery passwords cannot continue.
- Existing PIN/password/pattern: authenticate first, then complete mandatory recovery setup.
- Existing installation without recovery: **Forgot password?** opens device verification; cancelling preserves credentials. Successful verification requires recovery setup and primary setup again. Resume from the system prompt must stay in the recovery flow.
- Authenticator setup: QR/manual enrollment, invalid code, successful confirmation, cancellation, and rotation.
- Relaunch with TOTP selected: only authenticator codes or enabled biometrics unlock; old static credentials do not appear as fallback.
- Protected-app activity and accessibility overlay: authenticator unlock works and no recovery option appears.
- AppLock recovery: reject an incorrect recovery password; accept the correct one, require new primary setup, and preserve protected apps. Back and relaunch must not return to settings before enrollment.
