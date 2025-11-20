package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class MonitoringConfigService {

    @Autowired
    private LogMonitoringService monitoringService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WatchService watchService;
    private ExecutorService executorService;
    private String configFilePath;

    /**
     * Load monitoring rules from configuration file
     */
    public void loadConfiguration(String configPath) {
        this.configFilePath = configPath;
        File configFile = new File(configPath);
        
        if (!configFile.exists()) {
            log.warn("Configuration file not found: {}. Using default location.", configPath);
            // Try default location in resources
            try {
                configPath = getDefaultConfigPath();
                configFile = new File(configPath);
            } catch (Exception e) {
                log.error("Could not find default config file", e);
                return;
            }
        }

        try {
            List<LogMonitoringService.MonitoringRule> rules = 
                objectMapper.readValue(configFile, new TypeReference<List<LogMonitoringService.MonitoringRule>>() {});
            
            log.info("Loading {} monitoring rules from {}", rules.size(), configPath);
            
            // Clear existing rules and load new ones
            reloadRules(rules);
            
            // Start watching for config changes
            startWatchingConfig(configFile.getParent());
            
        } catch (IOException e) {
            log.error("Error loading configuration from {}", configPath, e);
        }
    }

    /**
     * Reload rules - removes old rules and adds new ones
     */
    private void reloadRules(List<LogMonitoringService.MonitoringRule> rules) {
        // Get current active rules and remove them
        // Note: You may want to add a method to LogMonitoringService to get all active rules
        // For now, we'll just add new rules (duplicates will be handled by stopMonitoring in addMonitoringRule)
        
        for (LogMonitoringService.MonitoringRule rule : rules) {
            try {
                monitoringService.addMonitoringRule(rule);
            } catch (Exception e) {
                log.error("Error adding monitoring rule for {}", rule.getLogFile(), e);
            }
        }
        
        log.info("Successfully loaded {} monitoring rules", rules.size());
    }

    /**
     * Start watching configuration file for changes (hot reload)
     */
    private void startWatchingConfig(String configDir) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(configDir);
            path.register(watchService, 
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "config-watcher");
                t.setDaemon(true);
                return t;
            });

            executorService.submit(() -> {
                try {
                    while (true) {
                        WatchKey key = watchService.take();
                        
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                Path changedFile = (Path) event.context();
                                if (changedFile.toString().endsWith("monitoring-rules.json")) {
                                    log.info("Configuration file changed, reloading...");
                                    // Small delay to ensure file write is complete
                                    Thread.sleep(500);
                                    loadConfiguration(configFilePath);
                                }
                            }
                        }
                        
                        if (!key.reset()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Config watcher interrupted");
                } catch (Exception e) {
                    log.error("Error in config watcher", e);
                }
            });
            
            log.info("Started watching configuration file for changes");
        } catch (IOException e) {
            log.error("Error setting up config file watcher", e);
        }
    }

    /**
     * Get default configuration path from resources
     */
    private String getDefaultConfigPath() throws Exception {
        // Try to get from resources first
        java.net.URL resource = getClass().getClassLoader().getResource("monitoring-rules.json");
        if (resource != null) {
            return resource.getPath();
        }
        
        // Fallback to current directory
        return "monitoring-rules.json";
    }

    /**
     * Cleanup on shutdown
     */
    public void shutdown() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing watch service", e);
            }
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}