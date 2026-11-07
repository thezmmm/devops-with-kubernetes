# Log Output

This application generates a random UUID when it starts and prints it with a UTC timestamp every 5 seconds.

## Run locally

```bash
docker build -t log-output:latest .
docker run --rm log-output:latest
```

Expected log output:

```text
2020-03-30T12:15:17.705Z: 8523ecb1-c716-4cb6-a044-b9e83bb98e43
2020-03-30T12:15:22.705Z: 8523ecb1-c716-4cb6-a044-b9e83bb98e43
```
