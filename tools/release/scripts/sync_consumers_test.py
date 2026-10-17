"""Тесты для scripts/sync_consumers.sh.

Скрипт подтягивает тег тулкита в локальные приложения-потребители
через `git subtree pull`. Потребители — соседние директории
с tools/release; директории без subtree пропускаются.

Git — часть SUT, поэтому фикстуры создаются реальным git в tmpdir
(как в whats_new_test.py, без mock).
"""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT = REPO_ROOT / "scripts" / "sync_consumers.sh"


def _git(*args, cwd):
    return subprocess.run(
        ["git", *args],
        cwd=cwd,
        capture_output=True,
        text=True,
        check=True,
        env={
            **os.environ,
            "GIT_AUTHOR_NAME": "Test",
            "GIT_AUTHOR_EMAIL": "t@t",
            "GIT_COMMITTER_NAME": "Test",
            "GIT_COMMITTER_EMAIL": "t@t",
        },
    )


class SyncConsumersTest(unittest.TestCase):
    """Синк тега тулкита в локальные потребители: pull / пропуск / ошибка."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

        # Тулкит-фикстура: тег v1.0.0 → изменение → тег v1.1.0
        toolkit = self.tmp / "toolkit"
        toolkit.mkdir()
        _git("init", cwd=toolkit)
        (toolkit / "scripts").mkdir()
        (toolkit / "scripts" / "tool.txt").write_text("v1\n")
        _git("add", ".", cwd=toolkit)
        _git("commit", "-m", "v1", cwd=toolkit)
        _git("tag", "v1.0.0", cwd=toolkit)
        (toolkit / "scripts" / "tool.txt").write_text("v2\n")
        _git("commit", "-am", "v2", cwd=toolkit)
        _git("tag", "v1.1.0", cwd=toolkit)
        self.toolkit = toolkit

        # Потребитель: subtree подключён на v1.0.0
        app = self.tmp / "App1"
        app.mkdir()
        _git("init", cwd=app)
        _git("commit", "--allow-empty", "-m", "init", cwd=app)
        _git(
            "subtree",
            "add",
            "--prefix",
            "tools/release",
            str(toolkit),
            "v1.0.0",
            "--squash",
            cwd=app,
        )
        self.app = app

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self, *args):
        return subprocess.run(
            [str(SCRIPT), *args],
            cwd=self.toolkit,
            capture_output=True,
            text=True,
            env={
                **os.environ,
                "CONSUMERS_DIR": str(self.tmp),
                "TOOLKIT_URL": str(self.toolkit),
            },
        )

    def test_pulls_latest_tag_into_consumer(self):
        result = self._run()

        self.assertEqual(result.returncode, 0, result.stderr)
        content = (self.app / "tools" / "release" / "scripts" / "tool.txt").read_text()
        self.assertEqual(content, "v2\n", "потребитель должен обновиться до v1.1.0")

    def test_explicit_tag_same_content_is_noop(self):
        result = self._run("v1.0.0")

        self.assertEqual(result.returncode, 0, result.stderr)

    def test_skips_dirs_without_subtree(self):
        stranger = self.tmp / "NotAConsumer"
        stranger.mkdir()
        (stranger / "marker.txt").write_text("x")

        result = self._run()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(
            (stranger / "marker.txt").exists(),
            "директория без tools/release не должна затрагиваться",
        )

    def test_failing_consumer_makes_exit_nonzero(self):
        broken = self.tmp / "Broken"
        (broken / "tools" / "release").mkdir(parents=True)  # не git-репозиторий

        result = self._run()

        self.assertNotEqual(
            result.returncode,
            0,
            "ошибка синка одного потребителя должна давать ненулевой exit code",
        )


if __name__ == "__main__":
    unittest.main()
