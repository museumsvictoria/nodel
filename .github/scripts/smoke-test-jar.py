"""Launch the TIP artifact and check the UI and build metadata it serves."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from urllib.request import urlopen


def check_host(host, directory, expected_sha):
    # Nodel records its listening port after extracting the bundled UI.
    port_file = directory / ".lastHTTPPort"
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if host.poll() is not None:
            raise RuntimeError(f"Nodel exited during startup with status {host.returncode}")
        if port_file.exists():
            port = port_file.read_text().strip()
            if port.isdecimal():
                break
        time.sleep(0.25)
    else:
        raise TimeoutError("Nodel did not finish startup within 60 seconds")

    base_url = f"http://127.0.0.1:{port}"
    with urlopen(f"{base_url}/", timeout=5) as response:
        if response.status != 200 or b"<!doctype html" not in response.read().lower():
            raise RuntimeError("The home page did not return the bundled HTML")
    with urlopen(f"{base_url}/build.json", timeout=5) as response:
        actual_sha = json.load(response)["id"]
    if actual_sha != expected_sha:
        raise RuntimeError(f"Expected commit {expected_sha}, got {actual_sha}")
    print(f"Nodel served its home page and build metadata for {actual_sha}")


def main(jar, expected_sha):
    with tempfile.TemporaryDirectory() as temp:
        directory = Path(temp)
        log_path = directory / "host.log"
        with log_path.open("w") as log:
            # The host shuts down on stdin EOF. Keep its console open for the probe.
            with subprocess.Popen(
                ["java", "-jar", str(jar.resolve()), "-p", "0"],
                cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
            ) as host:
                try:
                    check_host(host, directory, expected_sha)
                except Exception:
                    sys.stderr.write(log_path.read_text(errors="replace"))
                    raise
                finally:
                    host.terminate()
                    try:
                        host.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        host.kill()


if __name__ == "__main__":
    main(Path(sys.argv[1]), sys.argv[2])
