# Patreon download server

This is an all-in-one server for managing Patreon benefits and Stripe purchases for one-time software downloads.

## Features

- Serving content downloads
- Email sending via Mailgun
- PostgreSQL database support
- Patreon integration
  - Authentication
  - Pledge validation
  - Payment validation
  - Benefit/tier validation
  - Subscription dunning for declined payments (via email)
  - Telegram group membership management
- One-time download purchases
  - Stripe Checkout
  - Product info cards
  - Material You web UI
  - Purchase fulfillment via email and web
  - Localized prices using Purchasing Power Parity to make products affordable for more users
    - Conversion rates from [World Development Indicators](https://data.worldbank.org/indicator/PA.NUS.PPPC.RF)
    - MaxMind GeoIP database for price localization
- Telegram bot for admin actions
- Live content filtering during downloads
  - Watermark downloads
  - Combat piracy
  - Personalize downloads for each user

## Usage

The server can be built with `./gradlew shadowJar`.

A configuration file is required for the server to work properly. Copy `config.example.conf` and edit the values as necessary.

For full functionality, credentials for the following APIs and services are required:

- Stripe
- Patreon
- Telegram
- Mailgun
- MaxMind GeoIP database
- PostgreSQL

Finally, run the server once you've built and configured it: `java -jar build/libs/patreon-dl-server-0.0.1-all.jar -config=config.conf`

### Database

Optionally, the server can store information in a PostgreSQL database for easier debugging and analytics. This can be enabled in the config, as demonstrated by `config.example.conf`.

## Filters

The default content filter implementation is a simple pass-through filter that serves static data from storage with no modification. No watermarking filters are available in the public source code because making them public would defeat the purpose of watermarking, so you will need to create your own filter if you want to track the source of leaks.

The following objects are available to filters:

- File input stream (from static storage)
- Ktor application environment
- Ktor request and call
- Output stream (to return data to the user)

The Apache Commons Compress and Google Guava libraries are available for use in filters, along with the entirety of Ktor.

To make a custom filter, create a class that implements `dev.kdrag0n.patreondl.content.filters.ContentFilter` and re-compile the server with your class included. The filter can be activated by setting `web.contentFilter` to the fully-qualified class name in the server configuration file.
