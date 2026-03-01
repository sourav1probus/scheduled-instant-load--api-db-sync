package com.kalkitech.scheduled.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalkitech.scheduled.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class Db1CommandService {

    private static final Logger log = LoggerFactory.getLogger(Db1CommandService.class);

    private final JdbcTemplate db1;
    private final AppProperties props;
    private final ObjectMapper om;

    public Db1CommandService(@Qualifier("db1JdbcTemplate") JdbcTemplate db1, AppProperties props, ObjectMapper om) {
        this.db1 = db1;
        this.props = props;
        this.om = om;
    }

    public void deleteRequest(long commandId) {
        int deleted = db1.update(props.getDb1().getDeleteRequestSql(), commandId);
        log.info("DB1: deleted meter_command_request rows={} for command_id={}", deleted, commandId);
    }

    public Optional<Map<String, Object>> pollResponseUntilTerminal(long commandId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(props.getApi().getTimeOutMinutes()));
        long sleepMs = props.getApi().getPollIntervalMs();

        while (Instant.now().isBefore(deadline)) {
            Optional<Map<String, Object>> row = fetchResponseRow(commandId);
            if (row.isPresent()) {
                String status = String.valueOf(row.get().getOrDefault("status", "")).toLowerCase(Locale.ROOT).trim();
                if (isTerminal(status)) return row;
            }
            sleepSilently(sleepMs);
        }
        return fetchResponseRow(commandId);
    }

    private Optional<Map<String, Object>> fetchResponseRow(long commandId) {
        try {
            Map<String, Object> row = db1.queryForMap(props.getDb1().getSelectResponseSql(), commandId);
            return Optional.of(row);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void saveResponseToFile(long commandId, Map<String, Object> row) {
        try {
            Path dir = Path.of(props.getOutput().getDir());
            Files.createDirectories(dir);

            Path file = dir.resolve("command_" + commandId + ".txt");

            String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(row);
            String content = "command_id=" + commandId + System.lineSeparator()
                    + "saved_at=" + Instant.now() + System.lineSeparator()
                    + json + System.lineSeparator();

            Files.writeString(file, content);
            log.info("Saved DB1 meter_command_response to file: {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save response to file for commandId={}: {}", commandId, e.getMessage(), e);
        }
    }

    private static boolean isTerminal(String status) {
        return "success".equals(status) || "failed".equals(status) || "timeout".equals(status);
    }

    private static void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
