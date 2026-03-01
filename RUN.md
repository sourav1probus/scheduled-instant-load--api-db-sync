# Run from IntelliJ (Hardcoded Config)

## Steps
1. Open the project folder in IntelliJ (as Maven project).
2. Wait for Maven dependencies to download.
3. Run `ScheduledApiDbSyncApplication` (green play button).

## Output
- DB1 response files will be saved under:
  `./out/command-responses/command_<commandId>.txt`

## Schedules
- 5-min: 00:00, 00:05, 00:10, ...
- 30-min: 00:30, 01:30, 02:30, ... (HH:00 is expected to be handled by server's internal scheduler)
- Clash (:30): 30-min first, then 100ms gap, then 5-min (commandId is forced unique).

## Troubleshooting
### 1) `Failed to resolve '<host>' [A(1)]` / `UnknownHostException`
This is a DNS issue on your machine/network (the app cannot resolve the hostname).

Quick fixes (pick one):
- Change your DNS server to a public resolver (e.g., 8.8.8.8 / 1.1.1.1).
- Add a hosts entry (Windows):
  - Open Notepad as Administrator
  - Edit: `C:\Windows\System32\drivers\etc\hosts`
  - Add: `<HES_SERVER_IP>  hes-demo.probussense.com`
  - Save and retry.
