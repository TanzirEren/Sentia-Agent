# Signing Sentia (release APK)

The release build is signed with a key that only you have. Without it, the APK is signed with a debug key
(it still installs, but you cannot update it later with a different key, and it cannot go to the Play Store).

## Option A: let GitHub create the key (easiest, works from a phone)

1. Push this project to your repo (`developer-tanzir/Sentia-Agent`).
2. GitHub > Actions > **Generate signing keystore (run once)** > Run workflow.
3. Open the finished run > Artifacts > download **sentia-signing-KEEP-PRIVATE**.
4. Unzip it. You get `sentia-release.jks`, `keystore-base64.txt`, `secrets.txt`.
5. Repo > Settings > Secrets and variables > Actions > New repository secret. Add these 4:

| Secret name | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | full text inside `keystore-base64.txt` |
| `SIGNING_STORE_PASSWORD` | from `secrets.txt` |
| `SIGNING_KEY_ALIAS` | `sentia` |
| `SIGNING_KEY_PASSWORD` | from `secrets.txt` (same as store password) |

6. **Keep `sentia-release.jks` and the password somewhere safe** (cloud drive + a second copy).
   If you lose them you can never publish an update to the same app.
7. Delete the workflow run and its artifact. Delete `generate-keystore.yml` too if you like.

## Option B: make the key yourself (PC or Termux)

```bash
keytool -genkeypair -v -keystore sentia-release.jks -storetype PKCS12 \
  -alias sentia -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 sentia-release.jks      # paste the output into SIGNING_KEYSTORE_BASE64
```
Use the same password for the store and the key, then add the 4 secrets from the table above.

## Building

- Push to `main`, or Actions > **Build Sentia APK** > Run workflow.
- The APK appears under the run's Artifacts as `Sentia-v0.0.1`.
- Push a tag like `v0.0.1` and the APK is also attached to a GitHub Release.
- The build log prints the signing certificate, so you can confirm it is your key (not "Android Debug").

Never commit the `.jks` file or the passwords. `.gitignore` already blocks `*.jks`.
