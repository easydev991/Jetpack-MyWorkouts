#!/usr/bin/env python3
"""Генерация тёмных вариантов иконок упражнений (инверсия яркости).

Из исходных иконок (res/drawable/<name>.xml, серые непрозрачные заливки)
создаются копии res/drawable/<name>_dark.xml с цветами из DARK_MAP.
Заливки остаются непрозрачными, поэтому перекрытия путей работают
как в исходнике.

Светлая тема использует исходные ресурсы, тёмная — `_dark`; выбор делается
в Compose по теме (ресурсный квалификатор `-night` не подходит: он следует
только системной теме, а в приложении тема переключается внутри Compose).

Запуск из корня репо: python3 scripts/generate_dark_icon_variants.py
Скрипт пересоздаёт `_dark`-файлы при каждом запуске — правки в них вручную
делать бессмысленно, только через исходники + перезапуск.
"""

import re
import sys
from pathlib import Path

DRAWABLE_DIR = Path("app/src/main/res/drawable")

COLOR_RE = re.compile(r'android:(fillColor|strokeColor)="#([0-9A-Fa-f]{6})"')

# Тюнинг тёмной темы относительно линейной инверсии (255 - v): средние и
# светлые тона (экипировка) подняты ярче, максимум (фигуры человека) остаётся
# белым. Иерархия исходных оттенков сохранена.
#
# Экипировка (гантели, перекладины, скакалки) на исходных иконках —
# самый светлый серый #6C6C6C; фигуры людей — тёмные (#000000–#434343).
DARK_MAP = {
    "000000": "FFFFFF",
    "171717": "EDEDED",
    "2E2E2E": "DCDCDC",
    "434343": "C6C6C6",
    "6C6C6C": "D6D6D6",
}


def invert_gray(hex6: str) -> str:
    mapped = DARK_MAP.get(hex6)
    if mapped is not None:
        return mapped
    sys.exit(f"{hex6}: нет в DARK_MAP — добавьте тёмный вариант и перезапустите")


def main() -> None:
    sources = sorted(
        path
        for path in DRAWABLE_DIR.glob("*.xml")
        if not path.stem.startswith("ic_launcher") and not path.stem.endswith("_dark")
    )
    for source in sources:
        target = source.with_name(f"{source.stem}_dark.xml")
        converted = COLOR_RE.sub(
            lambda m: f'android:{m.group(1)}="#{invert_gray(m.group(2))}"',
            source.read_text(),
        )
        target.write_text(converted)
        print(f"{target.name}: создан")
    print("Готово.")


if __name__ == "__main__":
    main()
