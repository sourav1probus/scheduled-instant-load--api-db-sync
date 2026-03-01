# scheduled-api-db-sync (HES demo)

## What it does
### Scheduling
- 5-min: `00:00, 00:05, 00:10, ...`
- 30-min: `00:00, 00:30, 01:00, ...`
- Clash at `:00` or `:30`: **30-min first**, then **100ms gap**, then **5-min**.

### Flow per command
1) POST API with Bearer token + JSON DTO
2) DB1 (Postgres): delete `meter_command_request` by command_id
3) DB1: poll `meter_command_response` by command_id until status becomes `success/failed/timeout`
   - max wait = `app.api.time-out-minutes` (from your URL = 2)
4) Save the response row to text file:
   - `out/command-responses/command_<commandId>.txt`
5) If status == `success`:
   - 5-min: DB2 (StarRocks) `amr_instant_data` where `command_code = commandId`
   - 30-min: DB2 `amr_load_data` where `command_code = commandId` (may be multiple rows)
   - Insert same row(s) with `command_code = NULL` (UPSERT by insert)

## Secrets via env vars
```bash
export APP_BEARER_TOKEN="..."
export DB1_USER="postgres"
export DB1_PASS="probusdev"
export DB2_USER="root"
export DB2_PASS=""
```

## Run
```bash
mvn spring-boot:run
```


## If API fails with `Failed to resolve 'hes-demo.probussense.com'`
This is a **DNS / network** issue (not code). Quick checks on Windows:

```powershell
nslookup hes-demo.probussense.com
ping hes-demo.probussense.com
```

If `nslookup` fails:
- Connect to the **same VPN/network** where the HES demo is reachable, OR
- Use the server **IP** in `app.api.url`, OR
- Add a hosts entry (only if you know the correct IP):
  `C:\Windows\System32\drivers\etc\hosts`

## Java version
Project compiles for Java 17. Prefer running with JDK 17 as well (set `JAVA_HOME` to JDK 17).


## If DB1 Postgres connection fails with `Failed to initialize pool: This connection has been closed`
This is almost always **network / firewall / SSL / pg_hba** related.

Quick checks on Windows:
```powershell
Test-NetConnection dev-postgres.probussense.com -Port 5432
```

If it says `TcpTestSucceeded : False`:
- You need VPN / network access / whitelisting to reach Postgres.

If port is reachable but still closes:
- Try SSL: URL already includes `sslmode=prefer`. If your server **requires** SSL, change to `sslmode=require`.
- If your server **rejects** SSL, change to `sslmode=disable`.


## Hikari warnings: "Failed to validate connection ... connection closed"
We are NOT opening a new DB connection per API hit.
We use **HikariCP pools** (db1-pool/db2-pool). Each query borrows a connection from the pool and returns it.

These warnings happen when the DB server closes idle connections before Hikari expects.
So we tuned:
- shorter `maxLifetime`
- `keepaliveTime`
- `minimumIdle=0` and `idleTimeout`

## Clash at :30 and same epoch seconds
100ms gap doesn't change epoch seconds, so both calls could still get same epoch-second.
Now we generate `commandId` at send-time; if still equal we force INSTANT commandId to `+1`.
LOAD remains first, INSTANT second.
