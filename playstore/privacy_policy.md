# Privacy Policy — SaucedplussyTV

**Last updated: June 2026**

SaucedplussyTV is an unofficial, open-source Android TV client for Sauce+ (sauceplus.com). This policy describes what data the app stores and how it is used.

## Data We Collect

SaucedplussyTV collects and stores **only** the following on your device:

- **Session cookie** (`__Host-sp-sess`): The authentication cookie issued by Sauce+ after you log in. This is stored locally in Android's DataStore and is used solely to authenticate API requests to sauceplus.com.
- **User-Agent string**: The browser User-Agent captured from the login WebView. This is stored locally and sent with every API request to maintain Cloudflare session consistency.

## Data We Do Not Collect

- We do not collect your username or password. Login is performed in a WebView loading the official Sauce+ website — credentials are entered directly on sauceplus.com and are never accessible to the app.
- We do not collect analytics, crash reports, or usage data.
- We do not transmit any data to servers other than sauceplus.com and api.github.com (for update checks).
- We do not use advertising SDKs or third-party tracking.

## How Data Is Used

All stored data is used exclusively to authenticate and make API requests to Sauce+. No data leaves your device except as required to communicate with sauceplus.com.

## Data Storage and Deletion

All data is stored locally on your device using Android's DataStore. Logging out via the app settings clears all stored credentials immediately. Uninstalling the app removes all stored data.

## Third-Party Services

The app communicates with:
- **sauceplus.com** — content and authentication (subject to Sauce+'s own privacy policy)
- **api.github.com** — to check for app updates (no personal data is sent)

## Open Source

SaucedplussyTV is open source. The full source code is available at https://github.com/kinggh0stee/SauceplussyTV.

## Contact

For questions about this privacy policy, open an issue at https://github.com/kinggh0stee/SauceplussyTV/issues.
