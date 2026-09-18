#!/usr/bin/env python3
"""Отчёт по JUnit XML-результатам: python3 test_report.py <директория с XML>"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

GREEN = "\033[1;32m"
RED = "\033[1;31m"
RESET = "\033[0m"

if len(sys.argv) != 2:
    print("Использование: test_report.py <директория с XML-результатами>")
    sys.exit(1)

xml_files = list(Path(sys.argv[1]).rglob("*.xml"))
if not xml_files:
    print(f"XML-файлы с результатами тестов не найдены: {sys.argv[1]}")
    sys.exit(1)

total = 0
failed = 0
skipped = 0
failed_tests = []
class_stats = {}

for xml_file in xml_files:
    try:
        root = ET.parse(xml_file).getroot()
    except ET.ParseError:
        # ponytail: битый/недописанный XML пропускаем — итог посчитают остальные файлы
        continue
    for testcase in root.findall(".//testcase"):
        class_name = testcase.get("classname") or root.get("name") or xml_file.stem
        stats = class_stats.setdefault(
            class_name, {"total": 0, "failed": 0, "skipped": 0}
        )
        stats["total"] += 1
        total += 1
        # Явные is not None: у Element без детей truthiness = False
        failure = testcase.find("failure")
        if failure is None:
            failure = testcase.find("error")
        if failure is not None:
            # JUnit-семантика Assume: AssumptionViolatedException — это
            # пропуск теста (например, флейвор-гейт в androidTest), а не
            # провал. AGP пишет её в XML элементом <failure>.
            if "AssumptionViolatedException" in (failure.text or ""):
                skipped += 1
                stats["skipped"] += 1
                continue
            failed += 1
            stats["failed"] += 1
            failed_tests.append(f"{class_name}::{testcase.get('name', 'Unknown')}")

passed = total - failed - skipped

print("=" * 80)
print(
    f"{GREEN}✅ СБОРКА УСПЕШНА{RESET}"
    if failed == 0
    else f"{RED}❌ ТЕСТЫ ПРОВАЛИЛИСЬ{RESET}"
)
print("=" * 80)
print()
print("Статистика тестов:")
print(f"Всего тестов: {total}")
print(f"{GREEN}Успешные: {passed}{RESET}")
if skipped > 0:
    print(f"Пропущенные (Assume): {skipped}")
if failed > 0:
    print(f"{RED}Упавшие: {failed}{RESET}")
    print(f"{RED}Список упавших тестов:{RESET}")
    for name in failed_tests:
        print(f"  - {name}")
print()

print("=" * 80)
print(f"Статистика по тестовым классам ({len(class_stats)} классов):")
print("=" * 80)

# ponytail: Gradle даже для сломанного класса пишет testcase initializationError,
# так что class_stats здесь пустым не бывает; guard не нужен
name_width = min(max([len("Класс"), *map(len, class_stats)]), 60)
header = f"{'Класс':<{name_width}} {'Упало':^8} {'Успешно':^10} {'Всего':^8}"
print(header)
print("-" * len(header))

for class_name, stats in sorted(
    class_stats.items(),
    key=lambda item: (item[1]["failed"], item[1]["total"]),
    reverse=True,
):
    failed_str = str(stats["failed"]).center(8)
    passed_str = str(stats["total"] - stats["failed"] - stats["skipped"]).center(10)
    if stats["failed"] > 0:
        failed_str = f"{RED}{failed_str}{RESET}"
    if stats["total"] > stats["failed"]:
        passed_str = f"{GREEN}{passed_str}{RESET}"
    total_str = str(stats["total"]).center(8)
    print(
        f"{class_name[:name_width]:<{name_width}} {failed_str} {passed_str} {total_str}"
    )

print("=" * 80)
sys.exit(1 if failed else 0)
