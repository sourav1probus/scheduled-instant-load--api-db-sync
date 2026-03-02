package com.kalkitech.scheduled.service;

import com.kalkitech.scheduled.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Db2StarrocksService {

    private static final Logger log = LoggerFactory.getLogger(Db2StarrocksService.class);

    private final NamedParameterJdbcTemplate db2;
    private final AppProperties props;

    public enum DataType { INSTANT, LOAD }

    public Db2StarrocksService(@Qualifier("db2NamedJdbcTemplate") NamedParameterJdbcTemplate db2, AppProperties props) {
        this.db2 = db2;
        this.props = props;
    }

    public void waitAndNullCommandCodeThenInsert(DataType type, long commandId) {
        String commandCode = String.valueOf(commandId);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(props.getDb2().getWaitSeconds()));

        List<Map<String, Object>> rows = List.of();
        while (Instant.now().isBefore(deadline)) {
            rows = fetchRows(type, commandCode);
            if (!rows.isEmpty()) break;
            sleepSilently(1000);
        }

        if (rows.isEmpty()) {
            log.warn("DB2: No rows found in {} for command_code={} within {}s", type, commandCode, props.getDb2().getWaitSeconds());
            return;
        }

        List<Map<String, Object>> toInsert = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> copy = new HashMap<>(r);
            copy.put("command_code", null);
            toInsert.add(copy);
        }

        batchInsert(type, toInsert);
    }

    private List<Map<String, Object>> fetchRows(DataType type, String commandCode) {
        String sql = (type == DataType.INSTANT)
                ? props.getDb2().getInstant().getSelectSql()
                : props.getDb2().getLoad().getSelectSql();
        try {
            return db2.queryForList(sql, Map.of("commandCode", commandCode));
        } catch (Exception e) {
            log.error("DB2: fetchRows failed for type={}, commandCode={}: {}", type, commandCode, e.getMessage(), e);
            return List.of();
        }
    }

    //DB2 vatchUpdate stop
    private void batchInsert(DataType type, List<Map<String, Object>> rows) {
        String sql = (type == DataType.INSTANT)
                ? props.getDb2().getInstant().getInsertSql()
                : props.getDb2().getLoad().getInsertSql();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object>[] batch = rows.toArray(new Map[0]);
            int[] counts = db2.batchUpdate(sql, batch);
            int total = Arrays.stream(counts).sum();
            log.info("DB2: batch insert done type={}, rows={}, affectedSum={}", type, rows.size(), total);
        } catch (Exception e) {
            log.error("DB2: batchInsert failed type={}: {}", type, e.getMessage(), e);
        }
    }

    private static void sleepSilently(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
