# Log Monitoring Service

A lightweight, real-time log file monitoring service built with Spring Boot that actively monitors specified log files for important patterns and generates standardized alerts when patterns are detected.

## Overview

This service is designed for microservices ecosystems where multiple services generate logs in different formats. It provides:

- **Real-time Monitoring**: Processes new log entries as soon as they are written to files
- **Configuration-Driven Rules**: All monitoring rules are externalized in a JSON configuration file
- **Dynamic Configuration**: Hot-reloads configuration changes without requiring application restart
- **Standardized Alerts**: Transforms all alerts into a consistent JSON format regardless of source log format
- **File Creation Handling**: Automatically starts monitoring files that don't exist initially when they are created later

## Features

### Core Capabilities

- ✅ **Real-time log file monitoring** using Apache Commons IO Tailer
- ✅ **Pattern matching** using regular expressions
- ✅ **Multiple log format support** (JSON, key-value pairs, plain text)
- ✅ **Automatic file detection** for files created after service startup
- ✅ **Hot configuration reload** - changes to rules are applied automatically
- ✅ **File rotation handling** - automatically handles log file rotation
- ✅ **Standardized alert format** - all alerts in consistent JSON structure

### Technical Features

- **Low Latency**: Polls files every 100ms for near real-time processing
- **Efficient**: Only reads new lines, maintains file position
- **Resilient**: Handles missing files gracefully, queues them for monitoring when created
- **Thread-Safe**: Concurrent file monitoring with proper resource management

## Architecture

### Components

1. **LogFileMonitor** (`LogFileMonitor.java`)
   - Core file monitoring using Apache Commons IO Tailer
   - Handles file existence checking and pending file queue
   - Manages background threads for file monitoring

2. **LogMonitoringService** (`LogMonitoringService.java`)
   - Orchestrates monitoring rules
   - Applies pattern matching to log lines
   - Triggers alert generation

3. **LogPatternMatcher** (`LogPatternMatcher.java`)
   - Regular expression pattern matching
   - Extracts matched groups from log lines

4. **AlertService** (`AlertService.java`)
   - Generates standardized JSON alerts
   - Sends alerts to configured destinations (console, webhook, etc.)

5. **MonitoringConfigService** (`MonitoringConfigService.java`)
   - Loads monitoring rules from JSON configuration
   - Watches configuration file for changes
   - Hot-reloads rules when configuration changes

## Getting Started

### Prerequisites

- Java 17 or later
- Maven 3.6+ or Gradle
- Spring Boot 3.3.0

### Building the Application

```bash
# Using Maven
./mvnw clean package

# Using Gradle
./gradlew build
```

### Running the Application

```bash
# Using Maven
./mvnw spring-boot:run

# Using Gradle
./gradlew bootRun

# Or run the JAR
java -jar target/spring-boot-complete-0.0.1-SNAPSHOT.jar
```

### Running Without Web Server

If you only need the monitoring service without the web server:

```bash
./mvnw spring-boot:run -- -no-web
```

## Configuration

### Monitoring Rules Configuration

Monitoring rules are defined in `src/main/resources/monitoring-rules.json`:

```json
[
  {
    "logFile": "/var/logs/payment-service.log",
    "pattern": "level=ERROR",
    "severity": "CRITICAL",
    "destination": "console"
  },
  {
    "logFile": "/var/logs/auth-service.log",
    "pattern": "\"level\":\\s*\"WARN\"",
    "severity": "WARNING",
    "destination": "console"
  }
]
```

### Configuration File Format

Each monitoring rule contains:

- **logFile** (string, required): Absolute or relative path to the log file to monitor
- **pattern** (string, required): Regular expression pattern to match in log lines
- **severity** (string, required): Alert severity level (e.g., CRITICAL, WARNING, INFO)
- **destination** (string, required): Where to send alerts (currently supports "console")

### Custom Configuration Path

You can specify a custom configuration file path:

```bash
java -Dmonitoring.config=/path/to/your/rules.json -jar target/spring-boot-complete-0.0.1-SNAPSHOT.jar
```

## Usage Examples

### Example 1: Key-Value Pair Log Format

**Log File** (`payment-service.log`):
```
timestamp=2025-09-19T12:30:05Z level=ERROR msg="Credit card declined" transaction_id=tx_124
```

**Configuration**:
```json
{
  "logFile": "/var/logs/payment-service.log",
  "pattern": "level=ERROR",
  "severity": "CRITICAL",
  "destination": "console"
}
```

### Example 2: JSON Log Format

**Log File** (`auth-service.log`):
```json
{"time": "2025-09-19T12:31:15Z", "level": "WARN", "message": "Failed login attempt", "ip_address": "192.168.1.100"}
```

**Configuration**:
```json
{
  "logFile": "/var/logs/auth-service.log",
  "pattern": "\"level\":\\s*\"WARN\"",
  "severity": "WARNING",
  "destination": "console"
}
```

## Alert Format

All alerts are generated in a standardized JSON format:

```json
{
  "timestamp": "2025-09-19T12:30:05Z",
  "severity": "CRITICAL",
  "sourceFile": "/var/logs/payment-service.log",
  "matchedPattern": "level=ERROR",
  "logLine": "timestamp=2025-09-19T12:30:05Z level=ERROR msg=\"Credit card declined\" transaction_id=tx_124",
  "metadata": {}
}
```

## How It Works

### File Monitoring Process

1. **Configuration Loading**: On startup, the service loads monitoring rules from the configuration file
2. **File Existence Check**: For each rule, checks if the log file exists
   - If exists: Starts monitoring immediately
   - If not exists: Queues the file for monitoring (checks every 5 seconds)
3. **Real-time Monitoring**: Uses Apache Commons IO Tailer to poll files every 100ms
4. **Pattern Matching**: Each new line is checked against the configured pattern
5. **Alert Generation**: When a pattern matches, generates and sends a standardized alert

### Hot Reload Process

1. **Configuration Watcher**: Monitors the configuration file for changes
2. **Change Detection**: Detects when the configuration file is modified
3. **Automatic Reload**: Reloads rules and updates monitoring without restart

### Pending Files Handling

- Files that don't exist when rules are loaded are queued
- Background thread checks every 5 seconds for file creation
- When a file appears, monitoring starts automatically
- Logs indicate when files are pending and when monitoring begins

## Project Structure

```
complete/
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── Application.java              # Main application entry point
│   │   │   ├── LogFileMonitor.java           # Core file monitoring service
│   │   │   ├── LogMonitoringService.java     # Monitoring orchestration
│   │   │   ├── LogPatternMatcher.java        # Pattern matching service
│   │   │   ├── AlertService.java             # Alert generation service
│   │   │   ├── MonitoringConfigService.java   # Configuration management
│   │   │   └── logs/                         # Sample log files
│   │   └── resources/
│   │       └── monitoring-rules.json          # Monitoring rules configuration
│   └── test/
│       └── java/com/example/
└── pom.xml                                    # Maven configuration
```

## Dependencies

- **Spring Boot 3.3.0**: Application framework
- **Apache Commons IO 2.15.1**: File tailing functionality
- **Lombok 1.18.32**: Reduces boilerplate code
- **Jackson**: JSON processing (included with Spring Boot)

## Logging

The application uses SLF4J with Logback (provided by Spring Boot). Log levels:

- **INFO**: General application flow, rule loading, file monitoring start/stop
- **WARN**: Missing files, configuration issues
- **DEBUG**: Pattern matching details, line processing
- **ERROR**: Exceptions, pattern matching errors

## Limitations and Considerations

1. **Polling Interval**: Files are polled every 100ms. For lower latency, reduce the interval (may increase CPU usage)
2. **File Paths**: Use absolute paths for production. Relative paths are relative to the working directory
3. **Pattern Matching**: Uses Java regular expressions. Ensure patterns are properly escaped for JSON config
4. **File Rotation**: Handled automatically, but very rapid rotations may miss some lines
5. **Concurrent Writes**: Works best with append-only log files

## Future Enhancements

Potential improvements:

- [ ] Webhook destination support for alerts
- [ ] Database storage for alert history
- [ ] REST API for dynamic rule management
- [ ] Metrics and monitoring endpoints
- [ ] Support for multiple pattern matching per file
- [ ] Alert rate limiting and deduplication
- [ ] Support for log file compression formats

## Troubleshooting

### Files Not Being Monitored

- Check file paths are correct (use absolute paths)
- Verify file permissions (read access required)
- Check logs for "file does not exist" warnings
- For pending files, wait up to 5 seconds after file creation

### Patterns Not Matching

- Verify regex pattern syntax
- Check for case sensitivity issues
- Use debug logging to see pattern matching results
- Test patterns with online regex testers

### Configuration Not Reloading

- Ensure configuration file is saved completely
- Check file watcher permissions
- Verify configuration JSON is valid
- Check logs for reload errors

## License

This project is for educational/demonstration purposes.

## References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Apache Commons IO](https://commons.apache.org/proper/commons-io/)
- [Java Regular Expressions](https://docs.oracle.com/javase/tutorial/essential/regex/)

