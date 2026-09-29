"""Exercise launcher inputs without downloading dependencies or starting a JVM."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class GameLauncherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="petclinic launcher ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.checkout = self.root / "game checkout"
        self.downloads = self.root / "Downloads"
        self.bin = self.root / "bin"
        self.runtime = self.root / "Java 17" / "bin"
        for directory in (self.checkout, self.downloads, self.bin, self.runtime):
            directory.mkdir(parents=True)
        source = Path(__file__).resolve().parents[1] / "run-game.sh"
        self.launcher = self.checkout / "run-game.sh"
        shutil.copyfile(source, self.launcher)
        self.capture = self.root / "launch.json"
        self.build_capture = self.root / "build.json"
        self.env = os.environ.copy()
        for key in ("JAVA_HOME", "BOOKING_RULES_FILE", "APPGLASS_AGENT_JAR", "APPGLASS_AGENT_PORT"):
            self.env.pop(key, None)
        self.env.update({
            "PATH": str(self.bin) + os.pathsep + self.env["PATH"],
            "XDG_DOWNLOAD_DIR": str(self.downloads),
            "TEST_GAME_CAPTURE": str(self.capture),
            "TEST_GAME_BUILD_CAPTURE": str(self.build_capture),
            "TEST_GAME_RUNTIME": str(self.runtime / "java"),
            "TEST_GAME_JAVA_VERSION": "21.0.9",
        })
        self.executable(self.bin / "java", '''#!/usr/bin/env python3
import os
print('openjdk version "' + os.environ['TEST_GAME_JAVA_VERSION'] + '"')
''')
        self.executable(self.runtime / "java", '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
Path(os.environ['TEST_GAME_CAPTURE']).write_text(json.dumps({
    'args': sys.argv[1:], 'cwd': str(Path.cwd()), 'java': sys.argv[0]
}))
''')
        self.executable(self.checkout / "gradlew", '''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
Path(os.environ['TEST_GAME_BUILD_CAPTURE']).write_text(json.dumps(sys.argv[1:]))
if os.environ.get('TEST_GAME_BUILD_FAIL'):
    raise SystemExit(1)
output = Path('build/game')
output.mkdir(parents=True)
(output / 'java').write_text(os.environ['TEST_GAME_RUNTIME'] + '\\n')
(output / 'jar').write_text(str(Path.cwd() / 'build/libs/a new version.jar') + '\\n')
''')

    @staticmethod
    def executable(path, content):
        path.write_text(content)
        path.chmod(0o755)

    @staticmethod
    def rules(path):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("[]\n")
        return path

    def run_launcher(self, *arguments, cwd=None):
        return subprocess.run(
            ["bash", str(self.launcher), *map(str, arguments)],
            cwd=cwd or self.checkout,
            env=self.env,
            text=True,
            capture_output=True,
            timeout=10,
        )

    def assert_launched(self, result, rules):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        capture = json.loads(self.capture.read_text())
        self.assertEqual(capture["java"], str(self.runtime / "java"))
        self.assertEqual(capture["cwd"], str(self.checkout))
        self.assertIn("--petclinic.booking-rules=file:" + str(rules.resolve()), capture["args"])
        self.assertIn(str(self.checkout / "build/libs/a new version.jar"), capture["args"])
        self.assertEqual(json.loads(self.build_capture.read_text()), ["prepareGame", "--console=plain"])
        return capture

    def test_no_argument_discovers_the_download_and_uses_the_build_toolchain(self):
        rules = self.rules(self.downloads / "booking-rules.json")
        result = self.run_launcher("--server.port=8081", "--server.servlet.context-path=/petclinic")
        capture = self.assert_launched(result, rules)
        self.assertEqual(capture["args"][-2:], ["--server.port=8081", "--server.servlet.context-path=/petclinic"])

    def test_an_empty_legacy_variable_still_discovers_the_file(self):
        rules = self.rules(self.checkout / "booking-rules.json")
        self.assert_launched(self.run_launcher(""), rules)

    def test_explicit_relative_file_and_agent_are_resolved_before_changing_directory(self):
        caller = self.root / "another directory"
        rules = self.rules(caller / "my booking rules.json")
        agent = caller / "agent file.jar"
        agent.write_bytes(b"example")
        self.env["APPGLASS_AGENT_JAR"] = agent.name
        self.env["APPGLASS_AGENT_PORT"] = "19999"
        self.env["BOOKING_RULES_FILE"] = "/missing/env/override.json"
        result = self.run_launcher(rules.name, "--spring.application.name=Game with spaces", cwd=caller)
        capture = self.assert_launched(result, rules)
        self.assertIn(f"-javaagent:{agent}=tracingServer=on,serverPort=19999", capture["args"])
        self.assertEqual(capture["args"][-1], "--spring.application.name=Game with spaces")

    def test_environment_file_takes_precedence_over_discovery(self):
        self.rules(self.downloads / "booking-rules.json")
        rules = self.rules(self.root / "operations.json")
        self.env["BOOKING_RULES_FILE"] = str(rules)
        self.assert_launched(self.run_launcher(), rules)

    def test_missing_explicit_file_names_the_error_and_suggests_the_actual_file(self):
        self.rules(self.checkout / "booking-rules.json")
        result = self.run_launcher("booking_rules.json")
        self.assertEqual(result.returncode, 2)
        self.assertIn("Cannot read booking rules file: booking_rules.json", result.stderr)
        self.assertIn("Did you mean: ./run-game.sh ./booking-rules.json", result.stderr)
        self.assertNotIn("Usage:", result.stderr)
        self.assertFalse(self.build_capture.exists())

    def test_missing_environment_file_does_not_silently_select_another_file(self):
        self.rules(self.downloads / "booking-rules.json")
        self.env["BOOKING_RULES_FILE"] = str(self.root / "missing.json")
        result = self.run_launcher()
        self.assertEqual(result.returncode, 2)
        self.assertIn("missing.json", result.stderr)
        self.assertFalse(self.build_capture.exists())

    def test_missing_download_explains_where_to_put_it(self):
        result = self.run_launcher()
        self.assertEqual(result.returncode, 2)
        self.assertIn("No booking rules file was found", result.stderr)
        self.assertIn("Downloads", result.stderr)
        self.assertFalse(self.build_capture.exists())

    def test_empty_file_and_directory_are_rejected_before_building(self):
        empty = self.checkout / "empty.json"
        empty.touch()
        for path, message in ((empty, "file is empty"), (self.downloads, "Cannot read booking rules")):
            with self.subTest(path=path):
                result = self.run_launcher(path)
                self.assertEqual(result.returncode, 2)
                self.assertIn(message, result.stderr)
                self.assertFalse(self.build_capture.exists())

    def test_old_java_and_invalid_java_home_are_reported_before_building(self):
        self.rules(self.checkout / "booking-rules.json")
        self.env["TEST_GAME_JAVA_VERSION"] = "1.8.0_442"
        result = self.run_launcher()
        self.assertEqual(result.returncode, 2)
        self.assertIn("Java 17 or newer", result.stderr)
        self.env["JAVA_HOME"] = str(self.root / "missing JDK")
        result = self.run_launcher()
        self.assertEqual(result.returncode, 2)
        self.assertIn("Cannot run Java", result.stderr)
        self.assertFalse(self.build_capture.exists())

    def test_build_failure_does_not_start_the_application(self):
        self.rules(self.checkout / "booking-rules.json")
        self.env["TEST_GAME_BUILD_FAIL"] = "1"
        result = self.run_launcher()
        self.assertEqual(result.returncode, 1)
        self.assertIn("game build failed", result.stderr)
        self.assertFalse(self.capture.exists())

    def test_help_does_not_require_rules_or_java(self):
        self.env["JAVA_HOME"] = str(self.root / "missing JDK")
        result = self.run_launcher("--help")
        self.assertEqual(result.returncode, 0)
        self.assertIn("Usage:", result.stdout)
        self.assertFalse(self.build_capture.exists())


if __name__ == "__main__":
    unittest.main()
