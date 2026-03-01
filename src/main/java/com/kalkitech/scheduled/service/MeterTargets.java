package com.kalkitech.scheduled.service;

import com.kalkitech.scheduled.config.AppProperties;
import com.kalkitech.scheduled.model.MeterTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads meter targets from a simple CSV file (default: ./meters.csv).
 *
 * Format:
 *   meterNumber,meterMake,ip
 *   EP1260052,EEO,2401:4900:9867:fd4::2
 *
 * Lines starting with # and blank lines are ignored.
 */
@Component
public class MeterTargets {

    private static final Logger log = LoggerFactory.getLogger(MeterTargets.class);

    private final AppProperties props;
    private volatile List<MeterTarget> cached;

    public MeterTargets(AppProperties props) {
        this.props = props;
        this.cached = load();
    }

    public List<MeterTarget> all() {
        return cached;
    }

    /** Backwards-compatible helper for orchestrator code. */
    public List<MeterTarget> list() {
        return all();
    }

    /** Backwards-compatible helper for orchestrator code. */
    public int size() {
        List<MeterTarget> m = all();
        return m == null ? 0 : m.size();
    }

    private List<MeterTarget> load() {
        // If disabled or file missing, fall back to single meter properties
        if (!props.getMeters().isEnabled()) {
            return List.of(fromProps());
        }

        String file = props.getMeters().getFile();
        Path path = Paths.get(file);
        if (!Files.exists(path)) {
            log.warn("Meters file not found at '{}'. Falling back to single meter from application.properties", path.toAbsolutePath());
            return List.of(fromProps());
        }

        List<MeterTarget> list = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;

                // Header support
                if (s.toLowerCase().startsWith("meternumber")) continue;

                String[] parts = s.split(",");
                if (parts.length < 3) {
                    log.warn("Skipping invalid meters.csv line (expected 3 columns): {}", s);
                    continue;
                }
                String meterNumber = parts[0].trim();
                String meterMake = parts[1].trim();
                String ip = parts[2].trim();
                if (meterNumber.isEmpty() || meterMake.isEmpty() || ip.isEmpty()) {
                    log.warn("Skipping invalid meters.csv line (blank value): {}", s);
                    continue;
                }
                list.add(new MeterTarget(meterNumber, meterMake, ip));
            }
        } catch (Exception e) {
            log.error("Failed to read meters file '{}'. Falling back to single meter from application.properties", path.toAbsolutePath(), e);
            return List.of(fromProps());
        }

        if (list.isEmpty()) {
            log.warn("No meter targets found in '{}'. Falling back to single meter from application.properties", path.toAbsolutePath());
            return List.of(fromProps());
        }

        log.info("Loaded {} meter target(s) from {}", list.size(), path.toAbsolutePath());
        return Collections.unmodifiableList(list);
    }

    private MeterTarget fromProps() {
        return new MeterTarget(props.getApi().getMeterNumber(), props.getApi().getMeterMake(), props.getApi().getIp());
    }
}
