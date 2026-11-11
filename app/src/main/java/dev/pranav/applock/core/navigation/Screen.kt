package dev.pranav.applock.core.navigation

sealed class Screen(val route: String) {
    object AppIntro : Screen("app_intro")
    object SetPassword : Screen("set_password")
    object SetPasswordPattern : Screen("set_password_pattern")
    object SetPasswordAlphanumeric : Screen("set_password_alphanumeric")
    object TotpSetup : Screen("totp_setup")
    object RecoverySetup : Screen("recovery_setup")
    object RecoveryUnlock : Screen("recovery_unlock")
    object DeviceCredentialRecovery : Screen("device_credential_recovery")
    object ChangePassword : Screen("change_password")
    object Main : Screen("main")
    object PasswordOverlay : Screen("password_overlay")
    object Settings : Screen("settings")
    object TriggerExclusions : Screen("trigger_exclusions")
    object AntiUninstall: Screen("anti_uninstall")
}
