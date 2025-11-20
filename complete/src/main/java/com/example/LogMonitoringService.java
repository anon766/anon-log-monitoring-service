package com.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LogMonitoringService {

    @Autowired
    private LogFileMonitor logFileMonitor;
     @Autowired
    private LogPatternMatcher patternMatcher;

    @Autowired
    private AlertService alertService;

    // @Autowired
    // private LogPatternMatcher patternMatcher;

    // @Autowired
    // private AlertService alertService;

    private final Map<String, MonitoringRule> activeRules = new ConcurrentHashMap<>();

    /**
     * Start monitoring a file with a specific rule
     */
    public void addMonitoringRule(MonitoringRule rule) {
        String filePath = rule.getLogFile();
        
        logFileMonitor.startMonitoring(filePath, line -> {
            log.debug("Received line from {}: {}", filePath, line);
            // This callback is called ONLY for new lines
            if (patternMatcher.matches(line, rule.getPattern())) {
                log.info("Pattern '{}' matched in {}: {}", rule.getPattern(), filePath, line);
                String alertJson = alertService.generateAlert(
                    filePath,
                    rule.getSeverity(),
                    rule.getPattern(),
                    line
                );
                alertService.sendAlert(alertJson, rule.getDestination());
            }
        });

        activeRules.put(filePath, rule);
        
        // Check if file is pending (doesn't exist yet)
        if (logFileMonitor.isPending(filePath)) {
            log.info("Added monitoring rule for: {} (file does not exist yet, will monitor when created)", filePath);
        } else {
            log.info("Added monitoring rule for: {}", filePath);
        }
    }

    /**
     * Remove monitoring rule
     */
    public void removeMonitoringRule(String filePath) {
        logFileMonitor.stopMonitoring(filePath);
        activeRules.remove(filePath);
        log.info("Removed monitoring rule for: {}", filePath);
    }

    /**
     * Data class for monitoring rules
     */
    public static class MonitoringRule {
        private String logFile;
        private String pattern;
        private String severity;
        private String destination;

        // Getters and setters
        public String getLogFile() { return logFile; }
        public void setLogFile(String logFile) { this.logFile = logFile; }
        
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        
        public String getDestination() { return destination; }
        public void setDestination(String destination) { this.destination = destination; }
    }
}