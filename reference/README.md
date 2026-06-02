# reference/

Scratch space for reverse-engineering the Sauce+ backend contract while building the fork.

Drop here (all git-ignored):
- `sauceplus.apk` — official Sauce+ Android client, for auth/API recon (see CLAUDE.md "Auth model").
- `*.har` — browser network captures of login / API calls.

Analyze the APK with: `unzip -o sauceplus.apk -d sauceplus_apk` then `strings sauceplus_apk/classes*.dex | grep -i sauceplus`.
