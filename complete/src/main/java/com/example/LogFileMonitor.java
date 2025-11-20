package com.example;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LogFileMonitor {

    private final Map<String, Tailer> activeTailers = new ConcurrentHashMap<>();
    private final Map<String, LineProcessor> pendingFiles = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private ScheduledExecutorService scheduledExecutor;

    @PostConstruct
    public void init() {
        // Start background thread to check for pending files
        scheduledExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "pending-file-checker");
            t.setDaemon(true);
            return t;
        });
        
        // Check for pending files every 5 seconds
        scheduledExecutor.scheduleWithFixedDelay(this::checkPendingFiles, 5, 5, TimeUnit.SECONDS);
        log.info("Started pending file checker");
    }

    /**
     * Start monitoring a log file. Only new lines will be processed.
     * If the file doesn't exist, it will be queued for monitoring when it appears.
     * 
     * @param filePath Path to the log file
     * @param lineProcessor Callback to process each new line
     */
    public void startMonitoring(String filePath, LineProcessor lineProcessor) {
        File logFile = new File(filePath);
        
        if (!logFile.exists()) {
            log.warn("Log file does not exist yet: {}. Will start monitoring when file is created.", filePath);
            // Store for later monitoring when file appears
            pendingFiles.put(filePath, lineProcessor);
            return;
        }

        // Stop existing tailer if already monitoring this file
        stopMonitoring(filePath);
        
        // Remove from pending if it was there
        pendingFiles.remove(filePath);

        // Delegate to internal method
        startMonitoringInternal(filePath, lineProcessor);
    }

    /**
     * Stop monitoring a specific file
     */
    public void stopMonitoring(String filePath) {
        Tailer tailer = activeTailers.remove(filePath);
        if (tailer != null) {
            tailer.stop();
            log.info("Stopped monitoring: {}", filePath);
        }
    }

    /**
     * Check pending files and start monitoring if they now exist
     */
    private void checkPendingFiles() {
        if (pendingFiles.isEmpty()) {
            return;
        }
        
        pendingFiles.entrySet().removeIf(entry -> {
            String filePath = entry.getKey();
            LineProcessor lineProcessor = entry.getValue();
            File logFile = new File(filePath);
            
            if (logFile.exists()) {
                log.info("Pending file now exists: {}. Starting monitoring...", filePath);
                // Start monitoring the file
                startMonitoringInternal(filePath, lineProcessor);
                return true; // Remove from pending
            }
            return false; // Keep in pending
        });
    }

    /**
     * Internal method to start monitoring (assumes file exists)
     */
    private void startMonitoringInternal(String filePath, LineProcessor lineProcessor) {
        File logFile = new File(filePath);
        
        if (!logFile.exists()) {
            log.warn("File does not exist: {}", filePath);
            return;
        }

        // Stop existing tailer if already monitoring this file
        stopMonitoring(filePath);

        TailerListener listener = new TailerListener() {
            @Override
            public void init(Tailer tailer) {
                log.info("Started monitoring file: {}", filePath);
            }

            @Override
            public void fileNotFound() {
                log.warn("File not found: {}", filePath);
            }

            @Override
            public void fileRotated() {
                log.info("File rotated: {}", filePath);
                // Tailer automatically handles rotation and continues reading from new file
            }

            @Override
            public void handle(String line) {
                // This is called ONLY for new lines that haven't been read yet
                try {
                    log.info("Processing line from {}: {}", filePath, line);
                    lineProcessor.process(line);
                } catch (Exception e) {
                    log.error("Error processing line from {}: {}", filePath, line, e);
                }
            }

            @Override
            public void handle(Exception ex) {
                log.error("Error reading file: {}", filePath, ex);
            }
        };

        // Create Tailer with:
        // - file: the log file to monitor
        // - listener: handles new lines
        // - delayMillis: polling interval (100ms for near real-time)
        // - end: true = start from end of file (only new lines), false = read from beginning
        // - reOpen: true = handle file rotation
        Tailer tailer = new Tailer(
            logFile,
            listener,
            100,      // Check every 100ms for new lines
            true,     // Start from END of file (only process new lines)
            true      // Reopen file if it's rotated
        );

        activeTailers.put(filePath, tailer);
        
        // Run tailer in background thread
        executorService.submit(tailer);
        
        log.info("Monitoring started for: {}", filePath);
    }

    /**
     * Stop all monitoring
     */
    @PreDestroy
    public void stopAll() {
        log.info("Stopping all log file monitors...");
        activeTailers.values().forEach(Tailer::stop);
        activeTailers.clear();
        pendingFiles.clear();
        
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        executorService.shutdown();
    }
    
    /**
     * Get count of pending files waiting to be monitored
     */
    public int getPendingFilesCount() {
        return pendingFiles.size();
    }
    
    /**
     * Check if a file is pending (waiting to be created)
     */
    public boolean isPending(String filePath) {
        return pendingFiles.containsKey(filePath);
    }

    /**
     * Functional interface for processing new log lines
     */
    @FunctionalInterface
    public interface LineProcessor {
        void process(String line) throws Exception;
    }
}