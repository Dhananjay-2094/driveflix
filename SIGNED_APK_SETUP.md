# Build Signed DriveFlix APK Online

This workflow now supports two signing modes.

## Option 1: Quick build without secrets

Just run the GitHub Action:

**Actions → Build Signed DriveFlix APK → Run workflow**

If no signing secrets exist, the workflow automatically generates a release keystore and builds a signed APK.

After the build, download both artifacts:

```text
DriveFlix-signed-release-apk
DriveFlix-generated-keystore-KEEP-SAFE
```

Keep the generated keystore safe. Future updates of the same app must be signed with the same keystore.

Generated keystore defaults:

```text
Keystore password: driveflix123
Key alias: driveflix
Key password: driveflix123
```

## Option 2: Recommended permanent signing using GitHub Secrets

Create your own keystore once:

```bash
keytool -genkeypair -v -keystore driveflix-release.keystore -alias driveflix -keyalg RSA -keysize 2048 -validity 10000
```

Convert it to Base64.

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("driveflix-release.keystore")) | Set-Content keystore-base64.txt
```

Add these GitHub secrets:

```text
RELEASE_KEYSTORE_BASE64 = content from keystore-base64.txt
RELEASE_KEYSTORE_PASSWORD = your keystore password
RELEASE_KEY_ALIAS = driveflix
RELEASE_KEY_PASSWORD = your key password
```

Then run the workflow again. It will use your permanent keystore.

## Important

Do not lose your release keystore. Android treats apps signed with different keystores as different apps, so updates may fail if the signing key changes.
