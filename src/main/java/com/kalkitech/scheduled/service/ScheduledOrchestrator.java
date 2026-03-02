package com.kalkitech.scheduled.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kalkitech.scheduled.config.AppProperties;
import com.kalkitech.scheduled.model.MeterTarget;
import com.kalkitech.scheduled.service.CommandDtoFactory.JobType;
import com.kalkitech.scheduled.service.Db2StarrocksService.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ScheduledOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ScheduledOrchestrator.class);
    private static final ReentrantLock SCHED_LOCK = new ReentrantLock();

    private final AppProperties props;
    private final Db1CommandService db1;
    private final Db2StarrocksService db2;
    private final ApiClient apiClient;
    private final CommandDtoFactory dtoFactory;
    private final MeterTargets meterTargets;
    private final PerMeterTaskQueue perMeterTaskQueue;
    private final ApiConcurrencyLimiter apiLimiter;

    private final ObjectMapper om = new ObjectMapper();

    public ScheduledOrchestrator(
            AppProperties props,
            Db1CommandService db1,
            Db2StarrocksService db2,
            ApiClient apiClient,
            CommandDtoFactory dtoFactory,
            MeterTargets meterTargets,
            PerMeterTaskQueue perMeterTaskQueue,
            ApiConcurrencyLimiter apiLimiter
    ) {
        this.props = props;
        this.db1 = db1;
        this.db2 = db2;
        this.apiClient = apiClient;
        this.dtoFactory = dtoFactory;
        this.meterTargets = meterTargets;
        this.perMeterTaskQueue = perMeterTaskQueue;
        this.apiLimiter = apiLimiter;
    }

    /**
     * INSTANT every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void runEvery5Min() {
        if (!props.getScheduling().isEnabled()) return;

        // Skip at :30 because load scheduler handles it + triggers instant after gap
        ZoneId zone = ZoneId.of(props.getScheduling().getZone());
        ZonedDateTime now = ZonedDateTime.now(zone);
        //skip if it's HH:30 within first 2 seconds which is  @Scheduled(cron = "0 30 * * * *")
//        if (now.getMinute() == 30 && now.getSecond() < 2) {

        // Revised condition to skip every :30 which is  @Scheduled(cron = "0 */30 * * * *")
        //for disabled load set it to 00:00 hrs that is why
          if((now.getMinute()%30 ==0) && now.getSecond()<2){
            log.info("[INSTANT_5MIN] Skipping at :30 because LOAD job will handle it");
            return;
        }

        if (!SCHED_LOCK.tryLock()) {
            log.warn("[INSTANT_5MIN] Scheduler busy, skipping this tick");
            return;
        }
        try {
            ZonedDateTime aligned5 = alignTo5Min(now);
            long commandId = commandIdBase(aligned5);
            runInstant5Min(aligned5, commandId, false);
        } finally {
            SCHED_LOCK.unlock();
        }
    }

    /**
     * LOAD at HH:30, and then trigger INSTANT after a small delay (clash prevention).
     * Both are queued per-meter to guarantee LOAD then INSTANT order for the same meter.
     */
//    @Scheduled(cron = "0 30 * * * *")

//    @Scheduled(cron = "0 0 0 * * *")

    @Scheduled(cron = "0 */30 * * * *")
    public void runLoadEveryHourAt30() {
        if (!props.getScheduling().isEnabled()) return;

        if (!SCHED_LOCK.tryLock()) {
            log.warn("[LOAD_30MIN] Scheduler busy, skipping this tick");
            return;
        }

        try {
            ZoneId zone = ZoneId.of(props.getScheduling().getZone());
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime alignedHalf = alignToHalfHour(now);

            long loadCommandId = commandIdBase(alignedHalf);
            runLoadAt30(alignedHalf, loadCommandId);

            // Keep the small delay (and makes commandIds easier to read/debug)
            sleepQuietly(Duration.ofMillis(props.getScheduling().getClashGapMs()));

            // Ensure INSTANT commandId range never overlaps LOAD commandId range at same slot.
            long instantCommandId = loadCommandId + meterTargets.size();
            runInstant5Min(alignedHalf, instantCommandId, true);

        } finally {
            SCHED_LOCK.unlock();
        }
    }

    private ZonedDateTime alignTo5Min(ZonedDateTime t) {
        int minute = t.getMinute();
        int alignedMin = (minute / 5) * 5;
        return t.withMinute(alignedMin).withSecond(0).withNano(0);
    }

    private ZonedDateTime alignToHalfHour(ZonedDateTime t) {
        return t.withMinute(30).withSecond(0).withNano(0);
    }

    private long commandIdBase(ZonedDateTime alignedSlotTime) {
        return alignedSlotTime.toEpochSecond();
    }

    private void runInstant5Min(ZonedDateTime alignedSlotTime, long commandId, boolean fromClash) {
        long slotEpoch = dtoFactory.epochSeconds(alignedSlotTime);

        // Per environment behaviour: times are expected in local offset seconds (Asia/Kolkata = +05:30)
        int offsetSec = alignedSlotTime.getOffset().getTotalSeconds();
        long baseCommandId = commandId + offsetSec;
        long slotEpochAdj = slotEpoch + offsetSec;

        List<MeterTarget> meters = meterTargets.list();
        String tag = fromClash ? "INSTANT_5MIN(clash)" : "INSTANT_5MIN";

        for (int i = 0; i < meters.size(); i++) {
            MeterTarget meter = meters.get(i);
            long cmdId = baseCommandId + i;

            ObjectNode dto = dtoFactory.build(JobType.INSTANT_5MIN, cmdId, slotEpochAdj, slotEpochAdj, meter);

            // Queue per meter: same meter never overlaps; different meters parallel
            perMeterTaskQueue.submit(meter.meterNumber(), () ->
                    runEndToEnd(tag, cmdId, dto, DataType.INSTANT, meter)
            );
        }
    }

    private void runLoadAt30(ZonedDateTime alignedSlotTime, long commandId) {
        long to = dtoFactory.epochSeconds(alignedSlotTime);
        long from = dtoFactory.epochSeconds(alignedSlotTime.minusMinutes(30));

        // Per environment behaviour: times are expected in local offset seconds (Asia/Kolkata = +05:30)
        int offsetSec = alignedSlotTime.getOffset().getTotalSeconds();
        long baseCommandId = commandId + offsetSec;
        from += offsetSec;
        to += offsetSec;

        List<MeterTarget> meters = meterTargets.list();

        for (int i = 0; i < meters.size(); i++) {
            MeterTarget meter = meters.get(i);
            long cmdId = baseCommandId + i;

            ObjectNode dto = dtoFactory.build(JobType.LOAD_30MIN, cmdId, from, to, meter);

            perMeterTaskQueue.submit(meter.meterNumber(), () ->
                    runEndToEnd("LOAD_30MIN", cmdId, dto, DataType.LOAD, meter)
            );
        }
    }

    private void runEndToEnd(String tag, long commandId, ObjectNode dto, DataType dataType, MeterTarget meter) {
        log.info("[{}] Sending API request commandId={}, meter={}, dto={}", tag, commandId, meter.meterNumber(), dto);

        ApiClient.ApiResult apiResp;
        boolean permit = false;
        try {
            // Global cap across all meters to avoid bursting the HES API (common cause of Connection reset).
            apiLimiter.acquire();
            permit = true;
            apiResp = apiClient.postRaw(dto).block();
        } catch (Exception e) {
            log.error("[{}] API call failed for commandId={}, meter={}: {}", tag, commandId, meter.meterNumber(), e.getMessage(), e);
            return;
        } finally {
            if (permit) {
                apiLimiter.release();
            }
        }

        if (apiResp == null) {
            log.error("[{}] API call returned null response for commandId={}, meter={}", tag, commandId, meter.meterNumber());
            return;
        }

        log.info("[{}] API status={} contentType={} bodyPreview={} meter={}",
                tag, apiResp.statusCode(), apiResp.contentType(), preview(apiResp.body()), meter.meterNumber());

        // Optional debug: parse JSON if possible
        try {
            JsonNode n = om.readTree(apiResp.body());
            log.debug("[{}] API JSON parsed for meter {}: {}", tag, meter.meterNumber(), n);
        } catch (Exception ignored) { }

        if (apiResp.statusCode() < 200 || apiResp.statusCode() >= 300) {
            log.warn("[{}] Non-2xx response, skipping DB steps for commandId={}, meter={}", tag, commandId, meter.meterNumber());
            return;
        }

        try {
            db1.deleteRequest(commandId);
        } catch (Exception e) {
            log.error("[{}] DB1 deleteRequest failed for commandId={}, meter={}: {}", tag, commandId, meter.meterNumber(), e.getMessage(), e);
        }

        Optional<java.util.Map<String, Object>> respRowOpt = db1.pollResponseUntilTerminal(commandId);
        if (respRowOpt.isEmpty()) {
            log.warn("[{}] DB1 meter_command_response not found for commandId={} within timeout window (meter={})", tag, commandId, meter.meterNumber());
            return;
        }

        var respRow = respRowOpt.get();
        db1.saveResponseToFile(commandId, respRow);

        String status = String.valueOf(respRow.getOrDefault("status", "")).toLowerCase(Locale.ROOT).trim();
        log.info("[{}] DB1 status for commandId={} is '{}' (meter={})", tag, commandId, status, meter.meterNumber());

        if ("success".equals(status)) {
            db2.waitAndNullCommandCodeThenInsert(dataType, commandId);
        } else {
            log.info("[{}] Skipping DB2 update because status is {} (meter={})", tag, status, meter.meterNumber());
        }
    }

    private void sleepQuietly(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String preview(String body) {
        if (body == null) return "null";
        int max = 200;
        if (body.length() <= max) return body;
        return body.substring(0, max) + "...";
    }
}
