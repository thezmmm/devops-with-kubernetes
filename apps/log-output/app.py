import sys
import time
from datetime import datetime, timezone
from uuid import uuid4

LOG_INTERVAL_SECONDS = 5
APP_ID = str(uuid4())

sys.stdout.reconfigure(line_buffering=True)

while True:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
    print(f"{now}: {APP_ID}", flush=True)
    time.sleep(LOG_INTERVAL_SECONDS)
