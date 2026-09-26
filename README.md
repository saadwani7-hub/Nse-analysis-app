# NSE AI Analyst — Android

Indian-market AI copilot with a mobile dashboard, live/near-live quote refresh, news checker, technical buy/sell signals, and AI trade-plan opinions.

## Included
- NIFTY 50, Bank Nifty and India VIX dashboard.
- Auto-refreshing quote view during market hours (15-second polling; provider may be delayed).
- Custom NSE/BSE watchlist.
- News checker for the Indian market or an individual stock/company.
- Technical signal engine: EMA20/50/200, RSI14, MACD, ATR14, volume ratio and 20-day breakout context.
- Human-readable opinions: Strong Buy / Buy / Hold / Sell, with regime and setup context.
- Conditional reference entry, invalidation/SL, targets, risk budget and paper quantity.
- AI chat for current-data-aware research, trade planning, catalysts, financial quality, valuation, order book, ownership and risk.
- Secure encrypted local API-key storage through Android Keystore.
- No broker order execution.

## Data note
The default public Yahoo Finance chart endpoint is not guaranteed to be exchange-grade real-time. For professional intraday use, the market-data layer should be connected to a licensed broker/exchange feed such as a broker WebSocket/API. The app is designed so the data layer can be replaced without changing the signal UI.

## Build environment
Android Studio Quail 4 / Android Gradle Plugin 9.4.0 / Gradle 9.6.0, compileSdk 36.

The current execution environment did not contain the Android SDK/build tools, so a binary APK could not be compiled here. The source project is build-ready on a standard Android build environment.
