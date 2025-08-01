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

The plugin supports multiple storage backends for player authentication data. Choose the option that best fits your server setup:

## Storage Options

### File Storage (Default)

The simplest option that stores player data in a local file.

**Configuration:**

```properties
# Use file storage (default)
use_mysql=false
use_mongodb=false
```

**Pros:** Easy setup, no external dependencies  
**Cons:** Not suitable for multi-server setups, limited scalability

### MySQL Storage

Recommended for multi-server networks and better performance.

**Configuration:**

```properties
# Enable MySQL storage
use_mysql=true
host=127.0.0.1
port=3306
database-name=minecraft
database-username=root
database-password=your_password
database-extra=
```

**Requirements:**

- MySQL/MariaDB server
- Database and user with appropriate permissions

### MongoDB Storage

Modern NoSQL solution with excellent performance and scalability.

**Configuration:**

```properties
# Enable MongoDB storage
use_mongodb=true
mongo-host=127.0.0.1
mongo-port=27017
mongo-database=minecraft
mongo-username=
mongo-password=
mongo-connection-string=
```

#### Adding MongoDB Support with mongodb-loader

To use MongoDB storage, you need to add the `mongodb-loader` dependency:

**For Server Administrators:**

1. **Download mongodb-loader**: Get the mongodb-loader JAR from the [here](https://www.spigotmc.org/resources/mongodb-loader.124666)
2. **Install the loader**: Place `mongodb-loader.jar` in your server's `plugins` directory
3. **Configure AlwaysOnline**: Set `use_mongodb=true` in your AlwaysOnline configuration
4. **Set up MongoDB connection**: Configure the MongoDB connection details in the config file

**For Plugin Developers:**

If you're building AlwaysOnline from source, the mongodb-loader dependency is already included:

```gradle
dependencies {
    compileOnly 'com.github.AshleyThew:mongodb-loader:main-SNAPSHOT'
}
```

**MongoDB Connection Examples:**

```properties
# Basic connection (no authentication)
use_mongodb=true
mongo-host=localhost
mongo-port=27017
mongo-database=minecraft
mongo-username=
mongo-password=

# Authenticated connection
use_mongodb=true
mongo-host=your-mongo-server.com
mongo-port=27017
mongo-database=minecraft
mongo-username=your_username
mongo-password=your_password

# Advanced connection with connection string
use_mongodb=true
mongo-connection-string=?ssl=true&authSource=admin
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
2. Start your Sponge server - the plugin will create a configuration file in `config/alwaysonline/`
3. Edit the configuration file with your preferred storage settings
4. Restart your Sponge server to apply the changes

The plugin will now protect your Sponge server during Mojang outages.

## Advanced Configuration

### Server Monitoring Settings

```properties
# How often to check Mojang server status (in ticks, 20 ticks = 1 second)
status-check-delay=1200

# Enable/disable different check methods
http-head-session-server=true
mojang-server-status=true

# Custom messages
message-mojang-offline=&5[&2AlwaysOnline&5]&a Mojang servers are now offline!
message-mojang-online=&5[&2AlwaysOnline&5]&a Mojang servers are now online!
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
