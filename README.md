# AlwaysOnline

A robust plugin that keeps your Minecraft server online even when Mojang's authentication servers are down.

## Overview

Tired of Mojang servers being offline and preventing players from joining your server? AlwaysOnline solves this problem by implementing a fallback authentication system that allows authenticated players to continue playing even during Mojang outages.

## How it Works

The plugin continuously monitors Mojang's session servers with a configurable delay. When the servers go offline, AlwaysOnline enters "offline mode" and:

- **Allows returning players** to login if their IP address matches their last authenticated session
- **Blocks new players** from joining during Mojang outages for security
- **Automatically switches back** to normal authentication when Mojang servers come back online

## Compatibility

Works with: **Bungeecord, Velocity, Spigot, Paper, Bukkit, Sponge**

**SpigotMC Resource:** https://www.spigotmc.org/resources/alwaysonline.66591/

## Installation & Setup

### Basic Installation

1. Download the AlwaysOnline plugin from [SpigotMC](https://www.spigotmc.org/resources/alwaysonline.66591/)
2. Place the plugin JAR file in your server's `plugins` directory
3. Start your server to generate the default configuration
4. Configure the plugin according to your needs (see Configuration section below)
5. Restart your server to apply changes

### Configuration

Settings live in `config.yml`, generated on first run with every option and a description above it.

**Upgrading from 6.3.x or earlier?** Nothing to do. The old flat `config.properties` is read once, every value is carried into `config.yml`, and the old file is renamed to `config.properties.old`. Anything in it that this version no longer uses is named in the startup log rather than silently dropped.

From then on, each start tops your `config.yml` up with options added in newer releases, using their defaults, and leaves the values you have already set alone. You never need to delete the config to pick up new settings. Keys the plugin does not recognise are kept at the bottom of the file, and an option written with no value falls back to its default.

The plugin supports multiple storage backends for player authentication data. Choose the option that best fits your server setup:

## Storage Options

### File Storage (Default)

The simplest option that stores player data in a local file.

**Configuration:**

```yaml
# Use file storage (default) - neither database enabled
storage:
  mysql:
    enabled: false
  mongodb:
    enabled: false
```

**Pros:** Easy setup, no external dependencies  
**Cons:** Not suitable for multi-server setups, limited scalability

### MySQL Storage

Recommended for multi-server networks and better performance.

**Configuration:**

```yaml
storage:
  mysql:
    enabled: true
    host: 127.0.0.1
    port: 3306
    database: minecraft
    username: root
    password: your_password
    # Extra parameters appended to the JDBC connection URL
    extra: ''
```

**Requirements:**

- MySQL/MariaDB server
- Database and user with appropriate permissions

### MongoDB Storage

Modern NoSQL solution with excellent performance and scalability.

**Configuration:**

```yaml
storage:
  mongodb:
    enabled: true
    host: 127.0.0.1
    port: 27017
    database: minecraft
    username: ''
    password: ''
    connection-string: ''
```

#### Adding MongoDB Support with mongodb-loader

To use MongoDB storage, you need to add the `mongodb-loader` dependency:

**For Server Administrators:**

1. **Download mongodb-loader**: Get the mongodb-loader JAR from the [here](https://www.spigotmc.org/resources/mongodb-loader.124666)
2. **Install the loader**: Place `mongodb-loader.jar` in your server's `plugins` directory
3. **Configure AlwaysOnline**: Set `storage.mongodb.enabled: true` in your AlwaysOnline configuration
4. **Set up MongoDB connection**: Configure the MongoDB connection details in the config file

**For Plugin Developers:**

If you're building AlwaysOnline from source, the mongodb-loader dependency is already included:

```gradle
dependencies {
    compileOnly 'com.github.AshleyThew:mongodb-loader:main-SNAPSHOT'
}
```

**MongoDB Connection Examples:**

Basic connection (no authentication):

```yaml
storage:
  mongodb:
    enabled: true
    host: localhost
    port: 27017
    database: minecraft
    username: ''
    password: ''
```

Authenticated connection:

```yaml
storage:
  mongodb:
    enabled: true
    host: your-mongo-server.com
    port: 27017
    database: minecraft
    username: your_username
    password: your_password
```

Connection string. When set, it is used instead of host, port, username and password:

```yaml
storage:
  mongodb:
    enabled: true
    connection-string: '?ssl=true&authSource=admin'
```

**MongoDB Requirements:**

- MongoDB server (local or remote)
- mongodb-loader plugin installed
- Appropriate database permissions if using authentication

### Performance Comparison

| Storage Type | Performance | Scalability | Multi-Server | Complexity |
| ------------ | ----------- | ----------- | ------------ | ---------- |
| File         | Good        | Low         | No           | Very Low   |
| MySQL        | Very Good   | High        | Yes          | Medium     |
| MongoDB      | Excellent   | Very High   | Yes          | Medium     |

## Sponge Support

To use AlwaysOnline with Sponge servers:

1. Download the AlwaysOnline plugin and place it in your Sponge server's `mods` directory
2. Start your Sponge server - the plugin will create `config.yml` in `config/alwaysonline/`
3. Edit the configuration file with your preferred storage settings
4. Restart your Sponge server to apply the changes

The plugin will now protect your Sponge server during Mojang outages.

## Advanced Configuration

### Server Monitoring Settings

```yaml
# How often to check Mojang server status, in seconds
check-interval: 60

checks:
  # Session server check, via https://sessionserver.mojang.com/
  http-head-session-server: true
  # How long to stay in offline mode after an outage is detected, in minutes.
  # Detecting the servers as down again during this period resets the timer.
  down-detector-lockout-minutes: 5

messages:
  # Broadcast on each transition. Set to null to disable either one.
  mojang-offline: '&5[&2AlwaysOnline&5]&a Mojang servers are now offline!'
  mojang-online: '&5[&2AlwaysOnline&5]&a Mojang servers are now online!'
```

### Status Change Notifications

AlwaysOnline can alert you whenever Mojang's session servers go offline or come back online, so staff know even when they aren't in-game. Every method is optional, disabled until configured, and can be combined with the others. All notifications are delivered from the async status-check thread so they never block the server.

The plain-text message used by most methods is shared (Discord has its own messages):

```yaml
notifications:
  # Set to null to disable one direction.
  message-offline: Mojang servers are now offline! Falling back to AlwaysOnline authentication.
  message-online: Mojang servers are back online! Normal authentication restored.
```

#### Discord Webhook

In Discord, open **Channel Settings → Integrations → Webhooks**, create a webhook and copy its URL. Notifications are sent as color-coded embeds (red for offline, green for online) with a timestamp.

```yaml
notifications:
  discord:
    webhook-url: https://discord.com/api/webhooks/...
    username: AlwaysOnline
    # Set to null to disable one direction.
    message-offline: Mojang servers are now offline! Falling back to AlwaysOnline authentication.
    message-online: Mojang servers are back online! Normal authentication restored.
```

#### Generic Webhook

POSTs a JSON payload to any URL — works with Slack-compatible endpoints, n8n, Zapier, home automation, custom dashboards and more:

```yaml
notifications:
  webhook:
    url: https://example.com/hooks/minecraft
```

```json
{ "plugin": "AlwaysOnline", "status": "offline", "message": "...", "timestamp": "2026-08-17T12:00:00Z" }
```

#### Telegram Bot

Create a bot with [@BotFather](https://t.me/BotFather) to get a token, and use [@userinfobot](https://t.me/userinfobot) to find your chat id (group chat ids also work):

```yaml
notifications:
  telegram:
    bot-token: '123456789:AA...'
    chat-id: '123456789'
```

#### ntfy (phone push, no account needed)

Subscribe to a topic in the [ntfy](https://ntfy.sh) app, then point the plugin at the same topic URL. Use a hard-to-guess topic name, since anyone who knows it can subscribe:

```yaml
notifications:
  ntfy:
    url: https://ntfy.sh/your-secret-topic
    # Only needed for protected topics / self-hosted servers with auth
    token: ''
```

#### Pushover

Requires a [Pushover](https://pushover.net) application token and your user key:

```yaml
notifications:
  pushover:
    token: azG...
    user: uQiR...
```

#### Gotify

For self-hosted [Gotify](https://gotify.net) servers — create an application to get a token:

```yaml
notifications:
  gotify:
    url: https://gotify.example.com
    token: A4...
```

#### Console Commands

Run any console command on each transition — trigger another plugin, toggle a maintenance mode, etc. No leading slash:

```yaml
notifications:
  commands:
    offline: say Mojang is down, hang tight!
    online: say Mojang is back online!
```

### Security Considerations

- **IP Address Validation**: Players can only login from their last authenticated IP address during outages
- **New Player Protection**: New players cannot join during Mojang outages to prevent unauthorized access
- **Automatic Recovery**: The plugin automatically returns to normal authentication when Mojang servers are restored

### Multi-Server Networks

For networks using multiple servers:

1. Use either MySQL or MongoDB storage (file storage won't work across servers)
2. Configure all servers to use the same database
3. Ensure all servers can connect to your chosen database server

## Troubleshooting

### Common Issues

**Database Connection Failed:**

- Verify database server is running and accessible
- Check connection credentials and permissions
- Ensure mongodb-loader is installed when using MongoDB

**Players Can't Login During Outages:**

- Check if the player has previously authenticated on your server
- Verify the player is connecting from the same IP address
- Review server logs for authentication errors

**Plugin Not Detecting Mojang Outages:**

- Check your server's internet connection
- Verify firewall settings allow outbound connections
- Try adjusting the `status-check-delay` setting

### Support

- **Issues**: Report bugs on the [GitHub repository](https://github.com/AshleyThew/always-online)
- **SpigotMC**: Get support on the [plugin page](https://www.spigotmc.org/resources/alwaysonline.66591/)
- **Discord**: Join our community discord for real-time help

## License

This project is licensed under the terms specified in the LICENSE file.
