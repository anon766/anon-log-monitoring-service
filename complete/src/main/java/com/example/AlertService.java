package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AlertService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    public static class Alert {
        private String timestamp;
        private String severity;
        private String sourceFile;
        private String matchedPattern;
        private String logLine;
        private Map<String, Object> metadata = new HashMap<>();
    }

    /**
     * Generate standardized alert in JSON format
     */
    public String generateAlert(String sourceFile, String severity, String pattern, String logLine) {
        Alert alert = new Alert();
        alert.setTimestamp(Instant.now().toString());
        alert.setSeverity(severity);
        alert.setSourceFile(sourceFile);
        alert.setMatchedPattern(pattern);
        alert.setLogLine(logLine);
        
        try {
            return objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("Error serializing alert", e);
            return "{\"error\":\"Failed to serialize alert\"}";
        }
    }

    /**
     * Send alert to configured destination
     */
    public void sendAlert(String alertJson, String destination) {
        // For now, just print to console
        // You can extend this to POST to webhook, send to queue, etc.
        System.out.println("ALERT: " + alertJson);
        log.info("Alert generated: {}", alertJson);
        
        // TODO: Implement webhook POST or other destinations
        // if (destination.startsWith("http://") || destination.startsWith("https://")) {
        //     // POST to webhook
        // }
    }
}