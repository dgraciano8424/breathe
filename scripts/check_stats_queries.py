"""Execute the DAO's real period queries against SQLite boundary fixtures."""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt").read_text(encoding="utf-8")
queries = dict((name, sql) for sql, name in re.findall(
    r'@Query\("{1,3}(.*?)"{1,3}\)\s+suspend fun (\w+)', source, re.S
))
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE intervention_events (packageName TEXT, appName TEXT, timestamp INTEGER, outcome TEXT, minutesSaved INTEGER)")
db.executemany("INSERT INTO intervention_events VALUES (?, ?, ?, ?, ?)", [
    ("a", "App A", 999, "DECLINED", 100),  # Outside the period.
    ("a", "App A", 1000, "DECLINED", 5),  # Exactly at midnight.
    ("a", "App A", 1001, "CONTINUED", 0),
    ("b", "App B", 1000, "DECLINED", 7),
])
expected = {
    "getAttemptCount": [(2,)],
    "getTotalAttempts": [(3,)],
    "getTotalDeclined": [(2,)],
    "getTotalMinutesSavedSince": [(12,)],
    "getTopApps": [("a", "App A", 2), ("b", "App B", 1)],
}
for name, rows in expected.items():
    actual = db.execute(queries[name], {"since": 1000, "pkg": "a"}).fetchall()
    assert actual == rows, (name, actual, rows)
    empty = db.execute(queries[name], {"since": 2000, "pkg": "a"}).fetchall()
    assert empty == ([] if name == "getTopApps" else [(0,)]), (name, empty)
db.close()
print("Passed: 5 real DAO queries, exact-midnight inclusion, prior-period exclusion, and empty-period results.")
