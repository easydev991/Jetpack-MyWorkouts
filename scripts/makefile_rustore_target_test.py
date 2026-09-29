"""Интеграционный тест Makefile target `rustore`.

Ловит баг 'subshell забыл переменную': если OUTPUT_FILE вычисляется в
одном shell-вызове, а используется в другом — скрипт получает пустой $2.

Реальные bundleRustoreRelease и rustore_publish.sh подменены: gradlew
создаёт fake AAB, tools/release/scripts/rustore_publish.sh пишет аргументы
в лог. Работает в изолированном tmpdir с собственным Makefile — реальный
gradle.properties не изменяется.
"""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

FAKE_PUBLISH_BODY = """\
#!/usr/bin/env bash
{
  echo "ARGS=$@"
  echo "RUSTORE_MODE=${RUSTORE_MODE:-}"
  echo "RUSTORE_VID=${RUSTORE_VID:-}"
} >> "$FAKE_PUBLISH_LOG"
exit 0
"""

FAKE_GRADLEW_BODY = """\
#!/usr/bin/env bash
mkdir -p app/build/outputs/bundle/rustoreRelease
printf 'fake-aab' > app/build/outputs/bundle/rustoreRelease/app-rustore-release.aab
exit 0
"""

# Нейтральный dummy: тест не зависит от дефолта Makefile
# и не требует правок app-id.
_DUMMY_APP_ID = "com.example.test"


class _MakefileTestBase(unittest.TestCase):
    """Общая изоляция: копия Makefile + fake rustore_publish.sh + gradlew + secrets.

    setUp заполняет self.tmp (изолированная FS), self.publish_log (для лога
    publish-скрипта). Подклассы могут дополнительно инициализировать git и т.п.
    """

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        # Изолированная копия Makefile + tools/release/scripts/ + gradlew fakes.
        shutil.copy(REPO_ROOT / "Makefile", self.tmp / "Makefile")
        scripts = self.tmp / "tools" / "release" / "scripts"
        scripts.mkdir(parents=True)
        publish = scripts / "rustore_publish.sh"
        publish.write_text(FAKE_PUBLISH_BODY)
        publish.chmod(0o755)
        # _generate_whats_new.sh нужен для prerequisite `whats-new` в rustore/rustore-draft.
        # Файл живёт в subtree тулкита (tools/release/scripts/).
        helper_src = (
            REPO_ROOT / "tools" / "release" / "scripts" / "_generate_whats_new.sh"
        )
        helper = scripts / "_generate_whats_new.sh"
        helper.write_text(helper_src.read_text())
        helper.chmod(0o755)
        gradlew = self.tmp / "gradlew"
        gradlew.write_text(FAKE_GRADLEW_BODY)
        gradlew.chmod(0o755)
        (self.tmp / "gradle.properties").write_text(
            f"VERSION_NAME={self.version_name}\nVERSION_CODE=20\n"
        )
        # _ensure_secrets проверяет .secrets/ и app/google-services.json.
        secrets = self.tmp / ".secrets"
        secrets.mkdir()
        (secrets / "rustore-credentials.json").write_text("{}")
        app = self.tmp / "app"
        app.mkdir()
        (app / "google-services.json").write_text("{}")
        self.publish_log = self.tmp / "publish_args.log"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)


class MakefileRustoreTargetTest(_MakefileTestBase):
    """make rustore: путь к AAB передаётся в rustore_publish.sh без пустых."""

    version_name = "9.9.9-test"

    def _run_make_rustore(self, env_overrides):
        env = os.environ.copy()
        env["RUSTORE_APP_ID"] = _DUMMY_APP_ID
        env.update(env_overrides)
        return subprocess.run(
            ["make", "rustore", "-C", str(self.tmp)],
            env=env,
            capture_output=True,
            text=True,
        )

    def test_publish_receives_aab_path(self):
        # Если OUTPUT_FILE не передаётся в скрипт — \$2 пустой → exit 1.
        result = self._run_make_rustore(
            {
                "SKIP_PUBLISH": "",  # явно: не skip, publish должен вызваться
                "FAKE_PUBLISH_LOG": str(self.publish_log),
            }
        )

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        args = self.publish_log.read_text().strip()
        self.assertIn(
            "myworkouts21.aab",
            args,
            f"$2 должен быть непустой AAB путь, got: {args!r}",
        )
        self.assertIn(
            "VERSION_CODE=21",
            (self.tmp / "gradle.properties").read_text(),
            "VERSION_CODE должен инкрементироваться с 20 до 21",
        )

    def test_skip_publish_does_not_call_publish(self):
        result = self._run_make_rustore({"SKIP_PUBLISH": "1"})

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        self.assertFalse(
            self.publish_log.exists(),
            "rustore_publish.sh не должен вызываться при SKIP_PUBLISH=1",
        )


class MakefileWhatsNewAndDraftTest(_MakefileTestBase):
    """make whats-new / rustore-draft / rustore-commit: helper + targets."""

    version_name = "1.1.0"

    def setUp(self):
        super().setUp()
        # Git репозиторий с тегом — _generate_whats_new.sh берёт его.
        env = {
            **os.environ,
            "GIT_AUTHOR_NAME": "Test",
            "GIT_AUTHOR_EMAIL": "t@t",
            "GIT_COMMITTER_NAME": "Test",
            "GIT_COMMITTER_EMAIL": "t@t",
        }
        subprocess.run(
            ["git", "init"], cwd=self.tmp, env=env, check=True, capture_output=True
        )
        subprocess.run(
            ["git", "commit", "--allow-empty", "-m", "Initial"],
            cwd=self.tmp,
            env=env,
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["git", "tag", "1.0.0"],
            cwd=self.tmp,
            env=env,
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["git", "commit", "--allow-empty", "-m", "Fix bug"],
            cwd=self.tmp,
            env=env,
            check=True,
            capture_output=True,
        )

    def _run(self, target, env_overrides=None):
        env = os.environ.copy()
        if env_overrides:
            env.update(env_overrides)
        env["RUSTORE_APP_ID"] = _DUMMY_APP_ID
        env["FAKE_PUBLISH_LOG"] = str(self.publish_log)
        return subprocess.run(
            ["make", target, "-C", str(self.tmp)],
            env=env,
            capture_output=True,
            text=True,
        )

    def test_whats_new_creates_file_from_git_log(self):
        result = self._run("whats-new")

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        out_file = (
            self.tmp
            / "fastlane"
            / "metadata"
            / "android"
            / "ru-RU"
            / "whats_new"
            / "1.1.0.txt"
        )
        self.assertTrue(
            out_file.exists(), f"whats_new файл должен быть создан: {out_file}"
        )
        content = out_file.read_text()
        self.assertIn("Что нового в 1.1.0", content)
        self.assertIn("- Fix bug", content)
        self.assertNotIn(
            "- Initial",
            content,
            "коммиты до тега 1.0.0 не должны попасть в release notes",
        )

    def test_whats_new_shows_existing_file_without_overwrite(self):
        out_file = (
            self.tmp
            / "fastlane"
            / "metadata"
            / "android"
            / "ru-RU"
            / "whats_new"
            / "1.1.0.txt"
        )
        out_file.parent.mkdir(parents=True)
        out_file.write_text("Hand-curated release notes\n")

        result = self._run("whats-new")

        self.assertEqual(result.returncode, 0)
        self.assertEqual(
            out_file.read_text(),
            "Hand-curated release notes\n",
            "существующий файл не должен перезаписываться",
        )
        self.assertIn(
            "Hand-curated release notes",
            result.stdout,
            "stdout должен содержать содержимое существующего файла",
        )

    def test_rustore_draft_creates_whats_new_and_skips_commit(self):
        # Основной сценарий: без whats_new/ → make rustore-draft
        # генерирует файл и не вызывает commit.
        self.assertFalse(
            (
                self.tmp
                / "fastlane"
                / "metadata"
                / "android"
                / "ru-RU"
                / "whats_new"
                / "1.1.0.txt"
            ).exists(),
            "precondition: whats_new файл ещё не существует",
        )

        result = self._run("rustore-draft")

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        out_file = (
            self.tmp
            / "fastlane"
            / "metadata"
            / "android"
            / "ru-RU"
            / "whats_new"
            / "1.1.0.txt"
        )
        self.assertTrue(
            out_file.exists(), "rustore-draft должен создать whats_new перед publish"
        )
        # Проверяем что скрипт получил RUSTORE_MODE=upload (без commit)
        log = self.publish_log.read_text() if self.publish_log.exists() else ""
        self.assertIn(
            "RUSTORE_MODE=upload",
            log,
            f"draft должен передать RUSTORE_MODE=upload, got log: {log!r}",
        )
        self.assertNotIn(
            "/commit", log, f"draft не должен вызывать /commit, got log: {log!r}"
        )

    def test_rustore_commit_validates_vid(self):
        # Без VID=... → usage и exit != 0
        result = self._run("rustore-commit")

        self.assertNotEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstderr={result.stderr!r}",
        )
        combined = result.stdout + result.stderr
        self.assertIn(
            "VID", combined, f"должно быть usage с упоминанием VID, got: {combined!r}"
        )
        self.assertFalse(
            self.publish_log.exists(),
            "rustore-commit без VID не должен вызывать rustore_publish.sh",
        )


class MakefileLoadSecretsKeystorePathTest(unittest.TestCase):
    """make -n _load_secrets: KEYSTORE_FILE собирается из $(APP_NAME), не хардкодом."""

    def test_keystore_path_uses_app_name(self):
        result = subprocess.run(
            ["make", "-n", "_load_secrets", "APP_NAME=otherapp", "-C", str(REPO_ROOT)],
            capture_output=True,
            text=True,
        )

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstderr={result.stderr!r}",
        )
        self.assertIn(
            "KEYSTORE_FILE=.secrets/keystore/otherapp-release.keystore",
            result.stdout,
            "KEYSTORE_FILE должен собираться из $(APP_NAME), не быть хардкодом",
        )


if __name__ == "__main__":
    unittest.main()
