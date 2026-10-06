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
for start, end, expression in re.findall(
    r"val MIGRATION_(\d+)_(\d+)\s*=.*?database.execSQL\((.*?)\)\s*}", source, re.S
):
    strings = re.findall(r'"""(.*?)"""|"([^"\n]*)"', expression, re.S)
    sql = "".join(a or b for a, b in strings).replace("${BlockedApp.DEFAULT_PAUSE_SECONDS}", default_seconds)
    migrations[int(start)] = (int(end), sql)
assert set(migrations) == {1, 2, 3, 4}, "Review checker when migration structure changes"
schema = json.loads((root / "app/schemas/com.dgraciano.breathe.data.db.BreatheDatabase/5.json").read_text(encoding="utf-8"))["database"]

for start in range(1, 6):
    with sqlite3.connect(":memory:") as db:
        db.execute("CREATE TABLE blocked_apps(packageName TEXT NOT NULL PRIMARY KEY, appName TEXT NOT NULL, addedAt INTEGER NOT NULL)")
        db.execute("INSERT INTO blocked_apps VALUES('com.demo', 'Demo', 123)")
        db.execute("CREATE TABLE quotes(id INTEGER PRIMARY KEY, text TEXT)")
        # Construct a populated legacy database at the requested version.
        for version in range(1, start):
            db.execute(migrations[version][1])
        if start >= 2:
            columns = "packageName, appName, timestamp, outcome, reason"
            db.execute(f"INSERT INTO intervention_events({columns}) VALUES('com.demo', 'Demo', 456, 'DECLINED', 'HABIT')")
        if start >= 3:
            db.execute("UPDATE intervention_events SET minutesSaved = 7")
        if start >= 4:
            db.execute("UPDATE blocked_apps SET pauseSeconds = 60")
        for version in range(start, 5):
            db.execute(migrations[version][1])
        # Compare type, nullability and primary key to the generated Room schema.
        for entity in schema["entities"]:
            actual = {row[1]: row for row in db.execute(f'PRAGMA table_info("{entity["tableName"]}")')}
            expected = {field["columnName"]: field for field in entity["fields"]}
            assert set(actual) == set(expected), (start, entity["tableName"], actual)
            for name, field in expected.items():
                assert actual[name][2] == field["affinity"], (start, name, actual[name])
                assert bool(actual[name][3]) == field["notNull"], (start, name, actual[name])
            assert [name for name, row in actual.items() if row[5]] == entity["primaryKey"]["columnNames"]
        assert db.execute("SELECT packageName, appName, addedAt, pauseSeconds FROM blocked_apps").fetchone() == ("com.demo", "Demo", 123, 60 if start >= 4 else int(default_seconds))
        rows = db.execute("SELECT packageName, timestamp, outcome, reason, minutesSaved FROM intervention_events").fetchall()
        assert rows == ([] if start == 1 else [("com.demo", 456, "DECLINED", "HABIT", 7 if start >= 3 else 0)])
        assert not db.execute("SELECT name FROM sqlite_master WHERE name='quotes'").fetchall()
print("Passed: upgrades from versions 1-5 preserve app settings/history and match the exported v5 Room schema.")
