import random
import subprocess
import time

while True:
    owner_id = random.randint(2, 5)
    subprocess.run([
        "curl",
        "-i",
        "-X", "POST",
        "http://localhost:8080/emails/owner",
        "-d", f"ownerId={owner_id}",
    ])
    time.sleep(5)
