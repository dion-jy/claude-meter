# Privacy Policy

**Last updated: November 2, 2026**

> 한국어 버전은 [PRIVACY_POLICY.ko.md](PRIVACY_POLICY.ko.md)를 참고하세요.

## Overview

ClaudeMeter ("the App") is an open-source Android utility that displays your Claude.ai (and optionally ChatGPT/Codex) usage information in real-time. This policy explains how the App handles your data.

## Data Collection

The App itself does **not** collect, store, or transmit any personal data to servers operated by the developer. However, the App includes the Google AdMob advertising SDK and Firebase Analytics, which collect certain data as described in the [Advertising](#advertising-google-admob) and [Usage Analytics](#usage-analytics-firebase) sections below.

### What the App stores locally on your device:
- **Claude session key**: Used to authenticate with the Claude.ai API. Stored in encrypted storage (Android EncryptedSharedPreferences, device-only).
- **ChatGPT credentials** (optional): If you connect Codex usage tracking, the access token and session cookies are stored in encrypted storage on your device only.
- **Organization ID**: Used to fetch your usage data. Stored locally.
- **Display preferences**: Your metric visibility and notification settings.
- **Usage history**: Recent usage snapshots kept locally for the forecast feature.

### What the App does NOT do:
- Does not share your Claude/ChatGPT credentials or usage data with third parties
- Does not send your data to any server operated by the developer

## Advertising (Google AdMob)

The App displays ads (banner and interstitial) served by **Google AdMob**. To serve and measure ads, the Google Mobile Ads SDK may automatically collect and share with Google:

- **Device or other IDs** — the Android **Advertising ID**
- **IP address** and coarse location derived from it
- **Ad interaction data** (impressions, clicks) and diagnostic information

This data is collected by Google, not by the developer, and is used for advertising, ad measurement, and fraud prevention. See:

- [How Google uses information from sites or apps that use its services](https://policies.google.com/technologies/partner-sites)
- [Google Privacy Policy](https://policies.google.com/privacy)

You can limit ad personalization or delete/reset your Advertising ID at any time in your device settings under **Settings → Google → Ads** (or **Settings → Privacy → Ads**).

## Usage Analytics (Firebase)

The App uses Google Analytics for Firebase to count how many accounts are saved (as 0, 1, 2 or 3+) and when an account is added or switched. Firebase also collects an app instance ID, basic device and app information, and coarse location derived from your IP address. The App does not send your email, account names, credentials or Claude/ChatGPT usage data to Firebase, and does not link this data to your Advertising ID. See the [Google Privacy Policy](https://policies.google.com/privacy).

## Network Requests

The App communicates with:

- **`claude.ai`** — to fetch your usage metrics, spending limit info, and prepaid balance (e.g. `/api/organizations/{id}/usage`)
- **`chatgpt.com`** (only if you enable Codex tracking) — to authenticate and fetch your Codex usage data
- **Google ad servers** — to load and display AdMob ads
- **Google Analytics for Firebase** — to send the counts described above

No other network requests are made.

## Data Security

- Your session key and tokens are stored only on your device, in encrypted storage
- All network communication uses HTTPS

## Third-Party Services

- **Google AdMob** is used to display ads, as described above.
- **Google Analytics for Firebase** is used to count saved accounts, as described above.
- This App is **not affiliated with, endorsed by, or officially connected to Anthropic or OpenAI** in any way. It uses the same API endpoints that the Claude.ai and ChatGPT web interfaces use.

## Children's Privacy

This App is not intended for use by children under 13.

## Changes

This policy may be updated occasionally. Changes will be posted to this repository.

## Contact

If you have questions about this policy, please open an issue on [GitHub](https://github.com/CUN-bjy/claude-meter).

---
