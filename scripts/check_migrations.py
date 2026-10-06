"""Run production migration SQL in SQLite and compare with Room's exported schema.

No Android emulator or additional packages required. This checks SQL and retained
rows; it does not replace an on-device Room upgrade test.
"""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt").read_text(encoding="utf-8")
model = (root / "app/src/main/java/com/dgraciano/breathe/data/model/BlockedApp.kt").read_text(encoding="utf-8")
default_seconds = re.search(r"const val DEFAULT_PAUSE_SECONDS = (\d+)", model).group(1)
migrations = {}
blocks = list(re.finditer(r"val MIGRATION_(\d+)_(\d+)\s*=", source))
for index, match in enumerate(blocks):
    body = source[match.end():blocks[index + 1].start() if index + 1 < len(blocks) else len(source)]
    expressions = re.findall(r'database\.execSQL\(\s*(""".*?"""(?:\.trimIndent\(\))?|"[^"\n]*"(?:\s*\+\s*"[^"\n]*")*)\s*\)', body, re.S)
    statements = []
    for expression in expressions:
        strings = re.findall(r'"""(.*?)"""|"([^"\n]*)"', expression, re.S)
        statements.append("".join(a or b for a, b in strings).replace("${BlockedApp.DEFAULT_PAUSE_SECONDS}", default_seconds))
    assert statements, match.group(0)
    migrations[int(match.group(1))] = (int(match.group(2)), statements)
assert set(migrations) == {1, 2, 3, 4, 5}, "Review checker when migration structure changes"
schema = json.loads((root / "app/schemas/com.dgraciano.breathe.data.db.BreatheDatabase/6.json").read_text(encoding="utf-8"))["database"]

for start in range(1, 7):
    with sqlite3.connect(":memory:") as db:
        db.execute("CREATE TABLE blocked_apps(packageName TEXT NOT NULL PRIMARY KEY, appName TEXT NOT NULL, addedAt INTEGER NOT NULL)")
        db.execute("INSERT INTO blocked_apps VALUES('com.demo', 'Demo', 123)")
        db.execute("CREATE TABLE quotes(id INTEGER PRIMARY KEY, text TEXT)")
        # Construct a populated legacy database at the requested version.
        for version in range(1, start):
            for statement in migrations[version][1]: db.execute(statement)
        if start >= 2:
            columns = "packageName, appName, timestamp, outcome, reason"
            db.execute(f"INSERT INTO intervention_events({columns}) VALUES('com.demo', 'Demo', 456, 'DECLINED', 'HABIT')")
        if start >= 3:
            db.execute("UPDATE intervention_events SET minutesSaved = 7")
        if start >= 4:
            db.execute("UPDATE blocked_apps SET pauseSeconds = 60")
        for version in range(start, 6):
            for statement in migrations[version][1]: db.execute(statement)
        # Compare type, nullability and primary key to the generated Room schema.
        for entity in schema["entities"]:
            actual = {row[1]: row for row in db.execute(f'PRAGMA table_info("{entity["tableName"]}")')}
            expected = {field["columnName"]: field for field in entity["fields"]}
            assert set(actual) == set(expected), (start, entity["tableName"], actual)
            for name, field in expected.items():
                assert actual[name][2] == field["affinity"], (start, name, actual[name])
                assert bool(actual[name][3]) == field["notNull"], (start, name, actual[name])
            assert [name for name, row in actual.items() if row[5]] == entity["primaryKey"]["columnNames"]
            indices = {row[1]: row for row in db.execute(f'PRAGMA index_list("{entity["tableName"]}")')}
            for expected_index in entity["indices"]:
                name = expected_index["name"]
                assert bool(indices[name][2]) == expected_index["unique"]
                assert [row[2] for row in db.execute(f'PRAGMA index_info("{name}")')] == expected_index["columnNames"]
        assert db.execute("SELECT packageName, appName, addedAt, pauseSeconds FROM blocked_apps").fetchone() == ("com.demo", "Demo", 123, 60 if start >= 4 else int(default_seconds))
        rows = db.execute("SELECT packageName, timestamp, outcome, reason, minutesSaved FROM intervention_events").fetchall()
        assert rows == ([] if start == 1 else [("com.demo", 456, "DECLINED", "HABIT", 7 if start >= 3 else 0)])
        assert not db.execute("SELECT name FROM sqlite_master WHERE name='quotes'").fetchall()
        assert db.execute("SELECT COUNT(*) FROM pending_choices").fetchone() == (0,)
        assert all(row[0] is None for row in db.execute("SELECT choiceId FROM intervention_events"))
print("Passed: upgrades from versions 1-6 preserve settings/history and match exported Room v6 tables and indices.")
