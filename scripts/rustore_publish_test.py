"""Smoke-test scripts/rustore_publish.sh: 4-шаговый happy-path с fake curl.

Проверяет только happy path — что скрипт проходит все 4 шага (auth →
create-draft → upload → submit) без ошибок при подменённом curl.
Реальные сетевые контракты RuStore не проверяются — для этого нужен
sandbox или test-mode у RuStore.

Подменяется ТОЛЬКО curl (через PATH). openssl/jq остаются реальными —
они нужны для подписи (openssl dgst -sign) и парсинга JSON (jq).
"""

import base64
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT = REPO_ROOT / "scripts" / "rustore_publish.sh"

FAKE_CURL_BODY = """\
#!/usr/bin/env python3
import json, os, sys
url = next(a for a in sys.argv[1:] if a.startswith("http"))
# curl с -G + --data-urlencode не включает query в URL — собираем полный URL
# для лога, чтобы тесты могли проверить page=/size=.
log_url = url
query_parts = [
    sys.argv[i + 1] for i, a in enumerate(sys.argv)
    if a == "--data-urlencode" and i + 1 < len(sys.argv)
]
if query_parts:
    log_url = url + "?" + "&".join(query_parts)
with open(os.environ["FAKE_CURL_LOG"], "a") as f:
    f.write(log_url + "\\n")
if url.endswith("/public/auth/"):
    print(json.dumps({"body": {"jwe": "fake.jwe.token"}}))
elif "page=" in " ".join(sys.argv):
    max_code = int(os.environ.get("FAKE_MAX_CODE", "20"))
    print(json.dumps({"body": {"content": [{"versionId": 1, "versionCode": max_code}]}}))
elif "/aab" in url:
    pass
elif "/commit" in url:
    pass
else:
    print(json.dumps({"body": 12345}))
"""


class _ModeTestBase(unittest.TestCase):
    """Общий setup для smoke/mode тестов rustore_publish.sh.

    Создаёт временный проект с gradle.properties, fake-AAB, RSA-ключом,
    fake-curl через PATH. _run() запускает SCRIPT с extra_env и возвращает
    CompletedProcess. Используется всеми тестами ниже.
    """

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.fake_bin = self.tmp / "bin"
        self.fake_bin.mkdir()
        self.cwd = self.tmp / "project"
        self.cwd.mkdir()
        self.log = self.tmp / "curl.log"
        version_name = "9.9.9-smoke"
        (self.cwd / "gradle.properties").write_text(
            f"VERSION_NAME={version_name}\nVERSION_CODE=99\n"
        )
        whats_new_dir = (
            self.cwd / "fastlane" / "metadata" / "android" / "ru-RU" / "whats_new"
        )
        whats_new_dir.mkdir(parents=True)
        (whats_new_dir / f"{version_name}.txt").write_text("Тестовый релиз\n")
        # Реальный RSA private key (PKCS8, base64) — иначе openssl в шаге 1
        # auth падает с "Could not find private key". Генерируем 2048-bit в
        # setUp (~0.5с).
        genpkey = subprocess.run(
            [
                "openssl",
                "genpkey",
                "-algorithm",
                "RSA",
                "-pkeyopt",
                "rsa_keygen_bits:2048",
            ],
            capture_output=True,
            check=True,
        )
        pkcs8 = subprocess.run(
            ["openssl", "pkcs8", "-topk8", "-nocrypt"],
            input=genpkey.stdout,
            capture_output=True,
            check=True,
        )
        creds = {
            "key_id": "test-key-id",
            "client_secret": base64.b64encode(pkcs8.stdout).decode(),
        }
        self.creds = self.cwd / "creds.json"
        self.creds.write_text(json.dumps(creds))
        self.aab = self.cwd / "app.aab"
        self.aab.write_bytes(b"fake-aab")
        curl = self.fake_bin / "curl"
        curl.write_text(FAKE_CURL_BODY)
        curl.chmod(0o755)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self, extra_env):
        env = os.environ.copy()
        env["PATH"] = f"{self.fake_bin}{os.pathsep}{env['PATH']}"
        env["FAKE_CURL_LOG"] = str(self.log)
        # Нейтральный dummy вместо app-специфичного id: тесты переезжают в общий
        # тулкит (Этап 2) и не должны знать идентификатор приложения.
        env["RUSTORE_APP_ID"] = "com.example.test"
        env.update(extra_env)
        return subprocess.run(
            [str(SCRIPT), str(self.creds), str(self.aab), "0"],
            cwd=self.cwd,
            env=env,
            capture_output=True,
            text=True,
        )


class RustorePublishHappyPathTest(_ModeTestBase):
    """Скрипт отрабатывает все 4 шага и завершается с exit 0."""

    def test_happy_path(self):
        # RUSTORE_MODE не задан → defaults to "all" в rustore_publish.sh:31.
        result = self._run({})

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\n"
            f"stdout={result.stdout!r}\nstderr={result.stderr!r}",
        )

        urls = self.log.read_text().splitlines()
        self.assertEqual(len(urls), 5, f"expected 5 curl calls, got {urls}")
        self.assertTrue(urls[0].endswith("/public/auth/"), urls[0])
        self.assertIn("page=", urls[1])
        self.assertIn("/version", urls[2])
        self.assertIn("/aab", urls[3])
        self.assertIn("/commit", urls[4])


class RustorePublishReleaseNotesMissingTest(unittest.TestCase):
    """Если release notes для VERSION_NAME отсутствует — exit != 0.

    Защита от регрессии: скрипт не должен молча слать пустой whatsNew
    или падать с непонятной ошибкой на шаге create-draft.
    """

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.cwd = self.tmp / "project"
        self.cwd.mkdir()
        self.version_name = "9.9.9-smoke"
        (self.cwd / "gradle.properties").write_text(
            f"VERSION_NAME={self.version_name}\nVERSION_CODE=99\n"
        )
        self.creds = self.cwd / "creds.json"
        self.creds.write_text(json.dumps({"key_id": "k", "client_secret": "c"}))
        self.aab = self.cwd / "app.aab"
        self.aab.write_bytes(b"fake")
        # Намеренно НЕ создаём fastlane/metadata/.../whats_new/<VERSION>.txt.

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_missing_release_notes_exits_with_error(self):
        # Скрипт требует RUSTORE_APP_ID до проверки release notes — задаём dummy,
        # чтобы тест проверял именно сообщения о release notes.
        env = os.environ.copy()
        env["RUSTORE_APP_ID"] = "com.example.test"
        result = subprocess.run(
            [str(SCRIPT), str(self.creds), str(self.aab)],
            cwd=self.cwd,
            env=env,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(
            result.returncode,
            0,
            f"ожидался exit != 0, got {result.returncode}\n"
            f"stdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        self.assertIn(
            "Файл release notes не найден",
            result.stderr,
            f"stderr должен содержать сообщение о release notes, got {result.stderr!r}",
        )
        self.assertIn(
            self.version_name,
            result.stderr,
            f"stderr должен содержать VERSION_NAME, got {result.stderr!r}",
        )


class RustorePublishUploadModeTest(_ModeTestBase):
    """RUSTORE_MODE=upload: шаги 1-3 (auth+create-draft+upload AAB), без commit."""

    def test_upload_mode_skips_commit(self):
        result = self._run({"RUSTORE_MODE": "upload"})

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\n"
            f"stdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        urls = self.log.read_text().splitlines()
        self.assertEqual(len(urls), 4, f"expected 4 curl calls, got {urls}")
        self.assertTrue(urls[0].endswith("/public/auth/"), urls[0])
        self.assertIn("page=", urls[1])
        self.assertIn("/version", urls[2])
        self.assertIn("/aab", urls[3])
        self.assertFalse(
            any("/commit" in u for u in urls),
            f"upload mode не должен вызывать /commit, got {urls}",
        )

    def test_upload_mode_writes_vid_to_file(self):
        """После успешного create-draft VID записывается в .secrets/.last_rustore_vid.

        Makefile `rustore-draft` читает этот файл и подставляет VID в подсказку
        про `make rustore-commit VID=<vid>`. Если файл не создан — пользователь
        должен идти в Console искать VID руками.
        """
        result = self._run({"RUSTORE_MODE": "upload"})

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\n"
            f"stdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        vid_file = self.cwd / ".secrets" / ".last_rustore_vid"
        self.assertTrue(
            vid_file.exists(),
            f"VID-файл должен быть создан после create-draft: {vid_file}",
        )
        self.assertEqual(
            vid_file.read_text(),
            "12345",
            f"VID-файл должен содержать VID из create-draft ответа",
        )


class RustorePublishVersionCodeCheckTest(_ModeTestBase):
    """VERSION_CODE в gradle.properties должен быть строго выше max из RuStore.

    Защита от регрессии: без проверки скрипт принимает create-draft (HTTP 200),
    но upload AAB падает с HTTP 400 — в Console остаётся пустой черновик без файла.
    Скрипт должен упасть ДО create-draft с понятным сообщением.
    """

    def test_low_version_code_fails_before_create_draft(self):
        # В gradle.properties — 21, в RuStore fake — 22 (MODERATION).
        (self.cwd / "gradle.properties").write_text(
            "VERSION_NAME=9.9.9-smoke\nVERSION_CODE=21\n"
        )
        result = self._run({"RUSTORE_MODE": "upload", "FAKE_MAX_CODE": "22"})

        self.assertNotEqual(
            result.returncode,
            0,
            f"exit должен быть != 0 при VERSION_CODE ниже max, got {result.returncode}",
        )
        self.assertIn(
            "VERSION_CODE=21",
            result.stderr,
            f"stderr должен содержать локальный VERSION_CODE, got {result.stderr!r}",
        )
        self.assertIn(
            "max=22",
            result.stderr,
            f"stderr должен содержать max из RuStore, got {result.stderr!r}",
        )
        # create-draft и upload AAB НЕ должны быть вызваны.
        urls = self.log.read_text().splitlines()
        self.assertEqual(
            len(urls),
            2,
            f"после auth+GET /version скрипт должен упасть, "
            f"ожидалось 2 вызова, got {urls}",
        )
        self.assertTrue(urls[0].endswith("/public/auth/"), urls[0])
        self.assertIn("page=", urls[1])
        self.assertNotIn(
            any("/aab" in u for u in urls),
            urls,
            "create-draft/upload НЕ должны вызываться при низком VERSION_CODE",
        )


class RustorePublishCommitModeTest(_ModeTestBase):
    """RUSTORE_MODE=commit: только шаг 4 (commit), требует RUSTORE_VID."""

    def test_commit_mode_requires_vid(self):
        # Нет RUSTORE_VID — должен упасть до curl.
        result = self._run({"RUSTORE_MODE": "commit"})

        self.assertNotEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\nstderr={result.stderr!r}",
        )
        self.assertIn(
            "RUSTORE_VID",
            result.stderr,
            f"stderr должен упоминать RUSTORE_VID, got {result.stderr!r}",
        )
        self.assertFalse(
            self.log.exists(),
            "без RUSTORE_VID curl не должен вызываться вообще",
        )

    def test_commit_mode_calls_only_auth_and_commit(self):
        result = self._run({"RUSTORE_MODE": "commit", "RUSTORE_VID": "12345"})

        self.assertEqual(
            result.returncode,
            0,
            f"exit={result.returncode}\n"
            f"stdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        urls = self.log.read_text().splitlines()
        self.assertEqual(len(urls), 2, f"expected 2 curl calls, got {urls}")
        self.assertTrue(urls[0].endswith("/public/auth/"), urls[0])
        self.assertIn("/commit", urls[1])
        self.assertIn("12345", urls[1], "VID должен попасть в URL commit")
        # create-draft (POST /version без /aab) НЕ должен вызываться —
        # уже доказано: len==2 и urls[1] содержит /commit (других URL нет).


class RustorePublishAppIdRequiredTest(_ModeTestBase):
    """Без RUSTORE_APP_ID скрипт падает с подсказкой до любых сетевых вызовов.

    Защита от регрессии: APP_ID не хардкодится — без guard скрипт уходил бы
    в auth с пустым/чужим package id. Guard срабатывает раньше curl и openssl,
    поэтому тяжёлый setUp базы на результат не влияет.
    """

    def test_missing_app_id_fails_before_network(self):
        # Пустая строка для ${VAR:?} эквивалентна unset.
        result = self._run({"RUSTORE_APP_ID": ""})

        self.assertNotEqual(
            result.returncode,
            0,
            f"без RUSTORE_APP_ID ожидается exit != 0, got {result.returncode}\n"
            f"stdout={result.stdout!r}\nstderr={result.stderr!r}",
        )
        self.assertIn(
            "RUSTORE_APP_ID",
            result.stderr,
            f"stderr должен содержать имя переменной, got {result.stderr!r}",
        )
        self.assertIn(
            "Makefile",
            result.stderr,
            f"stderr должен содержать подсказку с Makefile, got {result.stderr!r}",
        )
        self.assertFalse(
            self.log.exists(),
            "guard должен сработать до первого сетевого вызова (curl)",
        )


if __name__ == "__main__":
    unittest.main()
