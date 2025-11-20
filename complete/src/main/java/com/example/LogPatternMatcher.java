package com.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Slf4j
@Service
public class LogPatternMatcher {

    /**
     * Check if a log line matches a pattern
     */
    public boolean matches(String logLine, String pattern) {
        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            Matcher matcher = compiledPattern.matcher(logLine);
            return matcher.find();
        } catch (Exception e) {
            log.error("Error matching pattern: {}", pattern, e);
            return false;
        }
    }

    /**
     * Extract matched groups from a log line
     */
    public String[] extractGroups(String logLine, String pattern) {
        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            Matcher matcher = compiledPattern.matcher(logLine);
            if (matcher.find()) {
                String[] groups = new String[matcher.groupCount() + 1];
                for (int i = 0; i <= matcher.groupCount(); i++) {
                    groups[i] = matcher.group(i);
                }
                return groups;
            }
        } catch (Exception e) {
            log.error("Error extracting groups from pattern: {}", pattern, e);
        }
        return new String[0];
    }
}