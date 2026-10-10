# Permanent personal APK signing

Every personal release APK must use the same private keystore. The app verifies the public SHA-256 fingerprint in `app/signing-certificate.properties` before packaging; release builds fail when credentials are missing, the alias/password is wrong, or the signer differs. Debug builds still use a development key and should not be used as personal updates.

The public Google OAuth web client ID has a repository default. `duaGoogleWebClientId` or `GOOGLE_CLIENT_ID` can override it. The ID is public; no Google client secret belongs in the APK.

## Store the key once

1. Save the alias and passwords in a password manager. If it supports file attachments, store the permanent keystore there too; otherwise store the keystore inside an encrypted backup as described below. Google Password Manager can hold the passwords and notes, but cannot store the keystore file itself.
2. Keep a second encrypted backup on a USB drive or private Drive. The prepared backup uses AES-256 ZIP encryption; use a compatible ZIP tool and keep its backup password separately. Test that it decrypts and restores the original keystore.
3. Keep a local working copy outside this repository with restricted permissions. A cloud development workspace can disappear and is not a permanent backup.
4. Never commit the keystore, password files, base64 encoding, or decrypted backup. Only public certificate fingerprints belong in the repository. GitHub Actions Secrets are a build copy, not a recoverable backup.

Do not generate a new key for each update. If the build says the certificate does not match, restore the saved key.

## Configure GitHub builds

In repository **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `DUA_RELEASE_KEYSTORE_BASE64` | Base64 of the original keystore file, on one line |
| `DUA_RELEASE_STORE_PASSWORD` | Keystore password |
| `DUA_RELEASE_KEY_ALIAS` | Original signing alias |
| `DUA_RELEASE_KEY_PASSWORD` | Private-key password |

Keep these values out of chat, issue comments, and workflow logs. After merging the workflow, use **Actions → Build personal APK → Run workflow → main**. It restores the key temporarily, validates its certificate, builds a shrunk release APK, verifies the APK signature, removes the runner's keystore, and attaches the APK/checksum/public fingerprints to a GitHub Release. It also keeps a 30-day workflow artifact. The workflow never uploads private signing files.

Only manual `main` builds receive signing credentials. Pull-request checks continue without those credentials. Release visibility follows the repository's visibility; this is a personal download, not a store release.

## Build locally

Load the four signing values from your private storage into environment variables:

- `DUA_RELEASE_STORE_FILE`: path to the original keystore.
- `DUA_RELEASE_STORE_PASSWORD`.
- `DUA_RELEASE_KEY_ALIAS`.
- `DUA_RELEASE_KEY_PASSWORD`.

Run `bash ./gradlew :app:validatePersonalReleaseSigning :app:assembleRelease --no-daemon`. Use environment variables for passwords rather than command-line arguments or tracked Gradle properties. Output: `app/build/outputs/apk/release/app-release.apk`.

Verify the finished APK:

```sh
python3 scripts/verify-apk-signature.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --apksigner "$ANDROID_HOME/build-tools/35.0.0/apksigner" \
  --output-dir /path/to/private-download-folder
```

## Google configuration and first install

In the existing Google Cloud **Android** OAuth client, keep package `com.dua.ai` and set the SHA-1 from `app/signing-certificate.properties`. Keep the existing **web** OAuth client. Supabase's Google provider must accept that same web client ID. Wait for Google configuration to propagate, then test the owner account on the phone.

The new permanent certificate differs from earlier cloud/debug APKs. Android will require uninstalling the old signer once before installing this APK. The owner confirmed there are no chats on the old install. If another installation contains data, sync/export it before uninstalling. Subsequent updates use this permanent key and install normally.

Build success proves packaging and signing, not successful phone sign-in, Supabase setup, or owner-lock behavior. Verify those separately.
