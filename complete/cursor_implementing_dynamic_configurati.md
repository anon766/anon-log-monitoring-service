# Implementing dynamic configuration for log monitoring service
_Exported on 11/20/2025 at 11:08:37 GMT+5:30 from Cursor (2.0.77)_

---

**User**

  Design and implement a lightweight service that actively monitors specified log files for important patterns. When a pattern is detected, the service should immediately generate a standardized alert and send it to a configured destination. Design and implement a lightweight service that actively monitors specified log files for important patterns. When a pattern is detected, the service should immediately generate a standardized alert and send it to a configured destination.



how to ensure the configuration file is immediately applied without restarting the application

---

**Cursor**


Reviewing the current project structure to understand the setup.



Searching for existing configuration handling and hot-reload approaches:



Summarizing options for configuration hot reload:

1) File Watcher on Configuration File (recommended)
Monitor the configuration file itself using the same approach as log monitoring:

```java
// ConfigurationWatcher.java
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class ConfigurationWatcher {
    
    private WatchService watchService;
    private Path configPath;
    private LogMonitoringService monitoringService;
    
    @PostConstruct
    public void init() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        configPath = Paths.get("config/monitoring-rules.json");
        
        // Watch the directory containing the config file
        configPath.getParent().register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
        
        startWatching();
    }
    
    @Async
    private void startWatching() {
        while (true) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changedFile = (Path) event.context();
                        if (changedFile.equals(configPath.getFileName())) {
                            reloadConfiguration();
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void reloadConfiguration() {
        try {
            // Parse new configuration
            MonitoringConfig newConfig = loadConfiguration();
            
            // Apply new configuration dynamically
            monitoringService.updateConfiguration(newConfig);
            
            log.info("Configuration reloaded successfully");
        } catch (Exception e) {
            log.error("Failed to reload configuration", e);
        }
    }
}
```

2) Polling-based configuration check
Periodically check the config file’s modification time:

```java
@Service
public class ConfigurationReloader {
    
    private File configFile;
    private long lastModified;
    private LogMonitoringService monitoringService;
    
    @PostConstruct
    public void init() {
        configFile = new File("config/monitoring-rules.json");
        lastModified = configFile.lastModified();
        startPolling();
    }
    
    @Scheduled(fixedDelay = 2000) // Check every 2 seconds
    public void checkForChanges() {
        long currentModified = configFile.lastModified();
        if (currentModified > lastModified) {
            reloadConfiguration();
            lastModified = currentModified;
        }
    }
    
    private void reloadConfiguration() {
        // Reload logic
    }
}
```

3) Spring Cloud Config with refresh endpoint
If using Spring Cloud Config, enable the `/actuator/refresh` endpoint:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: refresh,health
  endpoint:
    refresh:
      enabled: true
```

4) Custom actuator endpoint for manual reload
Create a custom endpoint to trigger reload:

```java
@Component
@Endpoint(id = "reload-config")
public class ConfigReloadEndpoint {
    
    private ConfigurationReloader reloader;
    
    @WriteOperation
    public String reload() {
        reloader.reloadConfiguration();
        return "Configuration reloaded";
    }
}
```

5) Complete implementation pattern
Combining monitoring with dynamic updates:

```java
@Service
@Slf4j
public class ConfigurationManager {
    
    private volatile MonitoringConfig currentConfig;
    private final Object configLock = new Object();
    private Path configFilePath;
    private WatchService watchService;
    
    @PostConstruct
    public void initialize() throws IOException {
        configFilePath = Paths.get("config/monitoring-rules.json");
        
        // Load initial configuration
        currentConfig = loadConfiguration();
        
        // Start watching for changes
        startConfigurationWatcher();
    }
    
    private void startConfigurationWatcher() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        
        // Watch the config file's parent directory
        configFilePath.getParent().register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE
        );
        
        // Start async watcher thread
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.poll(500, TimeUnit.MILLISECONDS);
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changedFile = (Path) event.context();
                            if (changedFile.equals(configFilePath.getFileName())) {
                                handleConfigurationChange();
                            }
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            log.warn("Watch key no longer valid");
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error watching configuration file", e);
                }
            }
        });
    }
    
    private void handleConfigurationChange() {
        try {
            // Small delay to ensure file write is complete
            Thread.sleep(100);
            
            // Validate JSON before loading
            MonitoringConfig newConfig = loadConfiguration();
            
            synchronized (configLock) {
                MonitoringConfig oldConfig = currentConfig;
                currentConfig = newConfig;
                
                // Notify monitoring service to update its rules
                updateMonitoringRules(oldConfig, newConfig);
                
                log.info("Configuration reloaded successfully at {}", 
                    Instant.now());
            }
        } catch (Exception e) {
            log.error("Failed to reload configuration: {}", e.getMessage(), e);
        }
    }
    
    private MonitoringConfig loadConfiguration() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configFilePath.toFile(), MonitoringConfig.class);
    }
    
    private void updateMonitoringRules(
            MonitoringConfig oldConfig, 
            MonitoringConfig newConfig) {
        // Compare old and new config
        // Add new watchers for new files
        // Remove watchers for removed files
        // Update patterns for existing files
    }
    
    public MonitoringConfig getCurrentConfig() {
        synchronized (configLock) {
            return currentConfig;
        }
    }
}
```

Key points:
1. Atomic reload: reload in a lock and update all references at once.
2. Validation: validate JSON/schema before applying changes.
3. Rollback: keep the previous config to revert on errors.
4. Thread safety: use volatile and synchronized appropriately.
5. File write completion: add a short delay or use file locking to avoid partial reads.

Best practice: use Java WatchService (approach 1) because:
- Event-driven (not polling)
- Low latency (< 100ms typically)
- No additional dependencies
- Similar pattern to log file monitoring
- Works cross-platform

Should I provide a complete implementation that integrates with your log monitoring service?

```java
// ConfigurationWatcher.java
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class ConfigurationWatcher {
    
    private WatchService watchService;
    private Path configPath;
    private LogMonitoringService monitoringService;
    
    @PostConstruct
    public void init() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        configPath = Paths.get("config/monitoring-rules.json");
        
        // Watch the directory containing the config file
        configPath.getParent().register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
        
        startWatching();
    }
    
    @Async
    private void startWatching() {
        while (true) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changedFile = (Path) event.context();
                        if (changedFile.equals(configPath.getFileName())) {
                            reloadConfiguration();
                        }
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void reloadConfiguration() {
        try {
            // Parse new configuration
            MonitoringConfig newConfig = loadConfiguration();
            
            // Apply new configuration dynamically
            monitoringService.updateConfiguration(newConfig);
            
            log.info("Configuration reloaded successfully");
        } catch (Exception e) {
            log.error("Failed to reload configuration", e);
        }
    }
}
```

```java
@Service
public class ConfigurationReloader {
    
    private File configFile;
    private long lastModified;
    private LogMonitoringService monitoringService;
    
    @PostConstruct
    public void init() {
        configFile = new File("config/monitoring-rules.json");
        lastModified = configFile.lastModified();
        startPolling();
    }
    
    @Scheduled(fixedDelay = 2000) // Check every 2 seconds
    public void checkForChanges() {
        long currentModified = configFile.lastModified();
        if (currentModified > lastModified) {
            reloadConfiguration();
            lastModified = currentModified;
        }
    }
    
    private void reloadConfiguration() {
        // Reload logic
    }
}
```

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: refresh,health
  endpoint:
    refresh:
      enabled: true
```

```java
@Component
@Endpoint(id = "reload-config")
public class ConfigReloadEndpoint {
    
    private ConfigurationReloader reloader;
    
    @WriteOperation
    public String reload() {
        reloader.reloadConfiguration();
        return "Configuration reloaded";
    }
}
```

```java
@Service
@Slf4j
public class ConfigurationManager {
    
    private volatile MonitoringConfig currentConfig;
    private final Object configLock = new Object();
    private Path configFilePath;
    private WatchService watchService;
    
    @PostConstruct
    public void initialize() throws IOException {
        configFilePath = Paths.get("config/monitoring-rules.json");
        
        // Load initial configuration
        currentConfig = loadConfiguration();
        
        // Start watching for changes
        startConfigurationWatcher();
    }
    
    private void startConfigurationWatcher() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        
        // Watch the config file's parent directory
        configFilePath.getParent().register(
            watchService,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE
        );
        
        // Start async watcher thread
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.poll(500, TimeUnit.MILLISECONDS);
                    if (key != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changedFile = (Path) event.context();
                            if (changedFile.equals(configFilePath.getFileName())) {
                                handleConfigurationChange();
                            }
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            log.warn("Watch key no longer valid");
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error watching configuration file", e);
                }
            }
        });
    }
    
    private void handleConfigurationChange() {
        try {
            // Small delay to ensure file write is complete
            Thread.sleep(100);
            
            // Validate JSON before loading
            MonitoringConfig newConfig = loadConfiguration();
            
            synchronized (configLock) {
                MonitoringConfig oldConfig = currentConfig;
                currentConfig = newConfig;
                
                // Notify monitoring service to update its rules
                updateMonitoringRules(oldConfig, newConfig);
                
                log.info("Configuration reloaded successfully at {}", 
                    Instant.now());
            }
        } catch (Exception e) {
            log.error("Failed to reload configuration: {}", e.getMessage(), e);
        }
    }
    
    private MonitoringConfig loadConfiguration() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(configFilePath.toFile(), MonitoringConfig.class);
    }
    
    private void updateMonitoringRules(
            MonitoringConfig oldConfig, 
            MonitoringConfig newConfig) {
        // Compare old and new config
        // Add new watchers for new files
        // Remove watchers for removed files
        // Update patterns for existing files
    }
    
    public MonitoringConfig getCurrentConfig() {
        synchronized (configLock) {
            return currentConfig;
        }
    }
}
```

