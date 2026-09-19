"""Тесты для scripts/_generate_whats_new.sh.

Helper генерирует whats_new/<VERSION>.txt из заголовков коммитов
с последнего релизного тега. Если файл уже есть — не перезаписывает,
а печатает его содержимое.

Использует реальный git init в tmpdir (без mock — git это и есть SUT
для истории).
"""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
HELPER = REPO_ROOT / "scripts" / "_generate_whats_new.sh"


def _git(*args, cwd):
    return subprocess.run(
        ["git", *args],
        cwd=cwd,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "GIT_AUTHOR_NAME": "Test",
            "GIT_AUTHOR_EMAIL": "t@t",
            "GIT_COMMITTER_NAME": "Test",
            "GIT_COMMITTER_EMAIL": "t@t",
        },
        check=True,
    )


class GenerateWhatsNewFromGitLogTest(unittest.TestCase):
    """Генерация whats_new из git log: первый релиз / между тегами / существующий файл."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        _git("init", cwd=self.tmp)
        _git("commit", "--allow-empty", "-m", "Initial commit", cwd=self.tmp)
        _git("tag", "1.0.0", cwd=self.tmp)
        _git("commit", "--allow-empty", "-m", "Fix crash on startup", cwd=self.tmp)
        _git("commit", "--allow-empty", "-m", "Add dark theme", cwd=self.tmp)
        self.output = self.tmp / "whats_new.txt"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_creates_file_with_commits_since_last_tag(self):
        subprocess.run(
            [str(HELPER), "1.1.0", str(self.output)],
            cwd=self.tmp,
            check=True,
            capture_output=True,
            text=True,
        )
        content = self.output.read_text()
        self.assertIn("Что нового в 1.1.0", content)
        self.assertIn("- Add dark theme", content)
        self.assertIn("- Fix crash on startup", content)
        self.assertNotIn(
            "- Initial commit",
            content,
            "коммиты до тега не должны попасть в release notes",
        )

    def test_existing_file_is_not_overwritten(self):
        self.output.write_text("Custom curated notes\n")
        result = subprocess.run(
            [str(HELPER), "1.1.0", str(self.output)],
            cwd=self.tmp,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0)
        self.assertEqual(self.output.read_text(), "Custom curated notes\n")
        self.assertIn(
            "Файл уже существует",
            result.stdout,
            "stdout должен информировать что файл уже есть",
        )
        self.assertIn(
            "Custom curated notes",
            result.stdout,
            "stdout должен содержать содержимое существующего файла",
        )

    def test_no_tags_shows_first_release_marker(self):
        no_tag = Path(tempfile.mkdtemp())
        try:
            _git("init", cwd=no_tag)
            _git("commit", "--allow-empty", "-m", "First commit", cwd=no_tag)
            out_file = no_tag / "release.txt"
            subprocess.run(
                [str(HELPER), "1.0.0", str(out_file)],
                cwd=no_tag,
                check=True,
                capture_output=True,
                text=True,
            )
            content = out_file.read_text()
            self.assertIn("Что нового в 1.0.0", content)
            self.assertIn("(первый релиз)", content)
        finally:
            shutil.rmtree(no_tag, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
