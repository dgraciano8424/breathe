"""Exercise the actual Room aggregate SQL against SQLite; Python standard library only."""
import re
import sqlite3
from pathlib import Path

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt").read_text(encoding="utf-8")
query = re.search(r'@Query\("""(.*?)"""\)\s*suspend fun getProgressTotals', source, re.S).group(1)
with sqlite3.connect(":memory:") as db:
    db.execute("CREATE TABLE intervention_events(timestamp INTEGER NOT NULL, outcome TEXT NOT NULL, minutesSaved INTEGER NOT NULL)")
    assert db.execute(query).fetchone() == (0, 0, 0, 0)
    day = 86_400_000
    midday = day // 2
    # Two Continue choices on one day count twice but only create one active day.
    db.executemany("INSERT INTO intervention_events VALUES (?, ?, ?)", [(midday, "OPENED", 0), (midday + 60_000, "OPENED", 0)])
    assert db.execute(query).fetchone() == (2, 1, 0, 0)
    # Gaps do not erase progress; old estimated savings remain available separately.
    db.executemany("INSERT INTO intervention_events VALUES (?, ?, ?)", [(midday + 3 * day, "DECLINED", 50000), (midday + 9 * day, "OPENED", 0), (midday + 10 * day, "UNKNOWN", 999)])
    assert db.execute(query).fetchone() == (4, 3, 1, 50000)
print("Progress SQL: empty history, duplicate-day choices, and nonconsecutive legacy history passed")
