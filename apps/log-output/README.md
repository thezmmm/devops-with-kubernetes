# Log Output

This application is implemented in Java and generates a random UUID when it starts. It prints the UUID together with a UTC timestamp every 5 seconds.

## Run locally

```bash
docker build -t log-output:latest .
docker run --rm log-output:latest
```

Expected log output:

```text
2026-11-07T15:50:00.123Z: 83364e18-70df-47cc-ad50-31d32ef8504d
2026-11-07T15:50:05.123Z: 83364e18-70df-47cc-ad50-31d32ef8504d
```
