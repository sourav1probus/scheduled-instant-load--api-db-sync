package com.kalkitech.scheduled.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String timeZone = "Asia/Kolkata";

    @NotNull
    private Api api = new Api();

    @NotNull
    private Scheduling scheduling = new Scheduling();

    /**
     * List of meter targets to run the job for. By default, the app reads targets
     * from a simple CSV file (project root) so the user can add/remove meters
     * without changing code.
     */
    @NotNull
    private Meters meters = new Meters();

    @NotNull
    private Output output = new Output();

    @NotNull
    private Db1 db1 = new Db1();

    @NotNull
    private Db2 db2 = new Db2();

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public Scheduling getScheduling() { return scheduling; }
    public void setScheduling(Scheduling scheduling) { this.scheduling = scheduling; }

    public Output getOutput() { return output; }
    public void setOutput(Output output) { this.output = output; }

    public Meters getMeters() { return meters; }
    public void setMeters(Meters meters) { this.meters = meters; }

    public Db1 getDb1() { return db1; }
    public void setDb1(Db1 db1) { this.db1 = db1; }

    public Db2 getDb2() { return db2; }
    public void setDb2(Db2 db2) { this.db2 = db2; }

    public static class Api {
        @NotBlank private String url;
        @NotBlank private String bearerToken;
        @Min(1) private int connectTimeoutMs = 5000;
        @Min(1) private int readTimeoutMs = 30000;
        @Min(1) private int timeOutMinutes = 2;
        @Min(100) private long pollIntervalMs = 1000;

        /** Reactor-Netty connection pool: max concurrent connections to API host */
        @Min(1) private int maxConnections = 20;
        /** Reactor-Netty connection pool: max idle time before we discard a connection (ms) */
        @Min(1) private long maxIdleTimeMs = 30_000;
        /** Reactor-Netty connection pool: max life time of a connection (ms) */
        @Min(1) private long maxLifeTimeMs = 300_000;
        /** Reactor-Netty connection pool: pending acquire timeout (ms) */
        @Min(1) private long pendingAcquireTimeoutMs = 60_000;

        /** Retry count for transient network failures (connection reset, timeouts, 5xx). 0 = disable. */
        @Min(0) private int retries = 2;
        /** Base backoff between retries (ms). */
        @Min(0) private long retryBackoffMs = 500;

        /** Global cap for simultaneous in-flight API calls across all meters. */
        @Min(1) private int maxInFlight = 5;
        /** Max time to wait to acquire an API-call permit (ms). */
        @Min(1) private long acquireTimeoutMs = 60_000;

        @NotBlank private String ip;
        @NotBlank private String meterMake;
        @NotBlank private String meterNumber;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getBearerToken() { return bearerToken; }
        public void setBearerToken(String bearerToken) { this.bearerToken = bearerToken; }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

        public int getTimeOutMinutes() { return timeOutMinutes; }
        public void setTimeOutMinutes(int timeOutMinutes) { this.timeOutMinutes = timeOutMinutes; }

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

        public int getMaxInFlight() { return maxInFlight; }
        public void setMaxInFlight(int maxInFlight) { this.maxInFlight = maxInFlight; }

        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }

        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

        public long getMaxIdleTimeMs() { return maxIdleTimeMs; }
        public void setMaxIdleTimeMs(long maxIdleTimeMs) { this.maxIdleTimeMs = maxIdleTimeMs; }

        public long getMaxLifeTimeMs() { return maxLifeTimeMs; }
        public void setMaxLifeTimeMs(long maxLifeTimeMs) { this.maxLifeTimeMs = maxLifeTimeMs; }

        public long getPendingAcquireTimeoutMs() { return pendingAcquireTimeoutMs; }
        public void setPendingAcquireTimeoutMs(long pendingAcquireTimeoutMs) { this.pendingAcquireTimeoutMs = pendingAcquireTimeoutMs; }

        public long getAcquireTimeoutMs() { return acquireTimeoutMs; }
        public void setAcquireTimeoutMs(long acquireTimeoutMs) { this.acquireTimeoutMs = acquireTimeoutMs; }

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }

        public String getMeterMake() { return meterMake; }
        public void setMeterMake(String meterMake) { this.meterMake = meterMake; }

        public String getMeterNumber() { return meterNumber; }
        public void setMeterNumber(String meterNumber) { this.meterNumber = meterNumber; }
    }

    public static class Scheduling {
        /**
         * Master switch for running all schedules in this application.
         * Defaults to true so older configs keep working.
         */
        private boolean enabled = true;

        /**
         * Time-zone used for aligning slot boundaries (commandId/from/to calculations).
         * Defaults to Asia/Kolkata.
         */
        @NotBlank private String zone = "Asia/Kolkata";

        @NotBlank private String cron5min = "0 */5 * * * *";
        // Only at HH:30 (HH:00 is typically handled by server's internal schedule)
        @NotBlank private String cron30min = "0 0 0 * * *";//"0 30 * * * *";
        @Min(0) private long clashGapMs = 100;
        /**
         * Some environments fire cron tasks a few milliseconds early/late. We add a small tolerance
         * when computing the aligned slot time so commandId/from/to match the intended boundary.
         */
        @Min(0) private long scheduleEarlyToleranceMs = 1100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }

        public String getCron5min() { return cron5min; }
        public void setCron5min(String cron5min) { this.cron5min = cron5min; }

        public String getCron30min() { return cron30min; }
        public void setCron30min(String cron30min) { this.cron30min = cron30min; }

        public long getClashGapMs() { return clashGapMs; }
        public void setClashGapMs(long clashGapMs) { this.clashGapMs = clashGapMs; }

        public long getScheduleEarlyToleranceMs() { return scheduleEarlyToleranceMs; }
        public void setScheduleEarlyToleranceMs(long scheduleEarlyToleranceMs) { this.scheduleEarlyToleranceMs = scheduleEarlyToleranceMs; }

        /**
         * Backward-compatible alias: some builds referenced `scheduleToleranceMs`.
         * Keep this for source compatibility.
         */
        public long getScheduleToleranceMs() { return scheduleEarlyToleranceMs; }

        /**
         * Backward-compatible alias: some builds referenced `scheduleToleranceMs`.
         * Setting this updates scheduleEarlyToleranceMs.
         */
        public void setScheduleToleranceMs(long scheduleToleranceMs) { this.scheduleEarlyToleranceMs = scheduleToleranceMs; }
    }

    public static class Output {
        @NotBlank private String dir = "./out/command-responses";
        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
    }

    public static class DbCommon {
        @NotBlank private String jdbcUrl;
        @NotBlank private String username;
        @NotBlank private String password;
        @NotBlank private String driverClassName;
        @Min(1) private int maximumPoolSize = 5;

        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
    }

    public static class Db1 extends DbCommon {
        @NotBlank private String deleteRequestSql;
        @NotBlank private String selectResponseSql;

        public String getDeleteRequestSql() { return deleteRequestSql; }
        public void setDeleteRequestSql(String deleteRequestSql) { this.deleteRequestSql = deleteRequestSql; }

        public String getSelectResponseSql() { return selectResponseSql; }
        public void setSelectResponseSql(String selectResponseSql) { this.selectResponseSql = selectResponseSql; }
    }

    public static class Db2 extends DbCommon {
        @Min(1) private int waitSeconds = 120;

        @NotNull private Instant instant = new Instant();
        @NotNull private Load load = new Load();

        public int getWaitSeconds() { return waitSeconds; }
        public void setWaitSeconds(int waitSeconds) { this.waitSeconds = waitSeconds; }

        public Instant getInstant() { return instant; }
        public void setInstant(Instant instant) { this.instant = instant; }

        public Load getLoad() { return load; }
        public void setLoad(Load load) { this.load = load; }

        public static class Instant {
            @NotBlank private String selectSql;
            @NotBlank private String insertSql;

            public String getSelectSql() { return selectSql; }
            public void setSelectSql(String selectSql) { this.selectSql = selectSql; }

            public String getInsertSql() { return insertSql; }
            public void setInsertSql(String insertSql) { this.insertSql = insertSql; }
        }

        public static class Load {
            @NotBlank private String selectSql;
            @NotBlank private String insertSql;

            public String getSelectSql() { return selectSql; }
            public void setSelectSql(String selectSql) { this.selectSql = selectSql; }

            public String getInsertSql() { return insertSql; }
            public void setInsertSql(String insertSql) { this.insertSql = insertSql; }
        }
    }

    public static class Meters {
        /**
         * Path to CSV file.
         * Default: meters.csv in project root so it can be edited easily.
         */
        @NotBlank
        private String file = "./meters.csv";

        /** Enable/disable file-based meter targets. */
        private boolean enabled = true;
        /**
         * Max parallel meter pipelines to run.
         * Controls overall concurrency while still keeping per-meter ordering.
         */
        private int parallelism = 8;



        /** Optional small delay between meters to avoid bursts. */
        private int perMeterGapMs = 50;

        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        public int getPerMeterGapMs() { return perMeterGapMs; }
        public void setPerMeterGapMs(int perMeterGapMs) { this.perMeterGapMs = perMeterGapMs; }
    }
}
