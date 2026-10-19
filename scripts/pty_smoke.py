"""End-to-end test through `sbt console`, using a local fake model, not Groq.

Run from the project directory: python3 scripts/pty_smoke.py
Requires macOS/Linux, Python 3, Java, and sbt on PATH. No Python dependencies.

Environment overrides:
  SCRAI_SMOKE_TASK  sbt task to run (default: runtime/console)
  SCRAI_SMOKE_CWD   directory for the sbt invocation (default: current dir)
"""

import errno
import fcntl
import http.server
import json
import os
import pty
import select
import signal
import struct
import tempfile
import threading
import termios
import time
from pathlib import Path


requests = []


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        requests.append(body)
        prompt = body["messages"][1]["content"]
        if "simulate failure" in prompt:
            self.send_response(429)
            self.end_headers()
            return
        if "slow generation" in prompt:
            time.sleep(3)
        content = "val aiAnswer =\n  40 + 2"
        payload = json.dumps({"choices": [{"message": {"content": content}, "finish_reason": "stop"}]}).encode()
        self.send_response(200)
        self.end_headers()
        try:
            self.wfile.write(payload)
        except BrokenPipeError:
            pass  # Expected when testing cancellation.

    def log_message(self, *args):
        pass


def main():
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    task = os.environ.get("SCRAI_SMOKE_TASK", "runtime/console")
    cwd = os.environ.get("SCRAI_SMOKE_CWD") or None
    with tempfile.TemporaryDirectory(prefix="scrai-smoke-") as tmp:
        config = Path(tmp) / "scrai.ini"
        config.write_text(f"[scrai]\nbase_url = http://127.0.0.1:{server.server_port}/v1\n")
        pid, fd = pty.fork()
        if pid == 0:
            fcntl.ioctl(0, termios.TIOCSWINSZ, struct.pack("HHHH", 40, 120, 0, 0))
            if cwd:
                os.chdir(cwd)
            os.environ.update(SCRAI_CONFIG=str(config), GROQ_API_KEY="test-key", TERM="xterm-256color")
            os.execvp("sbt", ["sbt", "-batch", task])
        transcript = bytearray()

        def read_until(needle, start=0, timeout=90):
            deadline = time.monotonic() + timeout
            while time.monotonic() < deadline:
                if needle in transcript[start:]:
                    return
                if select.select([fd], [], [], 0.2)[0]:
                    try:
                        chunk = os.read(fd, 65536)
                    except OSError as error:
                        if error.errno == errno.EIO:
                            break
                        raise
                    if not chunk:
                        break
                    transcript.extend(chunk)
                    Path("/tmp/scrai-pty-transcript.log").write_bytes(transcript)
            raise AssertionError(f"Missing {needle!r}:\n{transcript.decode(errors='replace')}")

        def send(text):
            os.write(fd, text)

        try:
            read_until(b"scala>")
            start = len(transcript)
            send(b"val words = List(\"pear\", \"apple\")\r")
            read_until(b'List("pear", "apple")', start)
            start = len(transcript)
            send(b"// define aiAnswer\x07")
            read_until(b"40 + 2", start)
            assert b"aiAnswer: Int" not in transcript[start:], "Generated code executed without Enter"
            assert len(requests) == 1
            assert "val words" in requests[0]["messages"][1]["content"]
            # Restore the original prompt; regenerate and explicitly execute.
            start = len(transcript)
            send(b"\x1bg")
            read_until(b"// define aiAnswer", start)
            start = len(transcript)
            send(b"\x07")
            read_until(b"40 + 2", start)
            start = len(transcript)
            send(b"\r\r")  # Scala's indentation-based REPL may need a blank line.
            read_until(b"aiAnswer: Int = 42", start)
            start = len(transcript)
            send(b"aiAnswer + 1\r")
            read_until(b"43", start)
            start = len(transcript)
            send(b":ai-clear\r")
            read_until(b"AI context cleared", start)
            start = len(transcript)
            send(b"// simulate failure\x07")
            read_until(b"HTTP 429", start)
            # Ctrl-A/Ctrl-K removes the preserved prompt before the next test.
            start = len(transcript)
            send(b"\x01\x0b// slow generation\x07")
            read_until(b"Generating Scala", start)
            time.sleep(0.3)
            start = len(transcript)
            send(b"\x03")
            read_until(b"Generation cancelled", start)
            # Clear the comment and verify compiler-backed completion.
            start = len(transcript)
            send(b"\x01\x0baiAns\t")
            read_until(b"aiAnswer", start)
            start = len(transcript)
            send(b" + 2\r")
            read_until(b"44", start)
            start = len(transcript)
            send(b":reset\r")
            read_until(b"scala>", start)
            start = len(transcript)
            send(b"1 + 1\r")
            read_until(b"res0: Int = 2", start)
            send(b":quit\r")
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                result, status = os.waitpid(pid, os.WNOHANG)
                if result:
                    assert os.waitstatus_to_exitcode(status) == 0, status
                    print("PASS: sbt console, evaluation, context, hotkey, multiline insertion without execution, restore, explicit execution, clear, HTTP failure, cancellation, completion, reset, quit")
                    return
                if select.select([fd], [], [], 0.2)[0]:
                    try:
                        transcript.extend(os.read(fd, 65536))
                    except OSError:
                        pass
            raise AssertionError("sbt did not exit after :quit")
        finally:
            Path("/tmp/scrai-pty-transcript.log").write_bytes(transcript)
            try:
                os.kill(pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            os.close(fd)
            server.shutdown()


if __name__ == "__main__":
    main()