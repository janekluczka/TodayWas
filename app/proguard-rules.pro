# Credential Manager (Google native sign-in) — keep rules per Android's Credential Manager
# setup docs. Inert while R8/minification is disabled for release builds (see app/build.gradle.kts),
# kept here so it's already correct if minification is turned on later.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
