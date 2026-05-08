# 등산로정보 → PostGIS 적재 가이드

## 1. 환경 설치

```bash
pip install psycopg2-binary sqlalchemy pyproj
```

---

## 2. PostGIS 테이블 생성

```sql
CREATE TABLE mountain_peak (
    id          SERIAL PRIMARY KEY,
    mntn_nm     VARCHAR(100),
    mntn_id     VARCHAR(20),
    spot_no     INTEGER,
    manage_type VARCHAR(50),
    detail      VARCHAR(200),
    geometry    GEOMETRY(POINT, 4326)
);

CREATE TABLE mountain_trail (
    id          SERIAL PRIMARY KEY,
    mntn_nm     VARCHAR(100),
    mntn_id     VARCHAR(20),
    trail_nm    VARCHAR(200),
    is_main     BOOLEAN,
    length_km   FLOAT,
    difficulty  VARCHAR(20),
    up_min      INTEGER,
    down_min    INTEGER,
    geometry    GEOMETRY(MULTILINESTRING, 4326)
);

CREATE INDEX ON mountain_peak  USING GIST (geometry);
CREATE INDEX ON mountain_trail USING GIST (geometry);
```

---

## 3. 전체 적재 스크립트

Esri JSON 파싱 + EPSG:5186 → WGS84 변환 + PostgreSQL INSERT를 한 번에 처리합니다.

```python
import json
import os
import psycopg2
from pyproj import Transformer

BASE_DIR = r"C:\Users\SSAFY\Desktop\등산로정보\광주광역시"

DB = {
    "host": "localhost",
    "port": 5432,
    "dbname": "surimap",
    "user": "postgres",
    "password": "password",
}

# EPSG:5186 (TM) → EPSG:4326 (WGS84)
tf = Transformer.from_crs("EPSG:5186", "EPSG:4326", always_xy=True)


def tm_to_wgs84(x, y):
    lon, lat = tf.transform(x, y)
    return lon, lat


def parse_peak(filepath):
    """PMNTN_SPOT_*.json → [(mntn_nm, mntn_id, spot_no, manage_type, detail, wkt), ...]"""
    with open(filepath, encoding="utf-8") as f:
        data = json.load(f)

    rows = []
    for feat in data.get("features", []):
        attrs = feat.get("attributes", {})
        geom  = feat.get("geometry", {})
        x, y  = geom.get("x"), geom.get("y")
        if x is None or y is None:
            continue
        lon, lat = tm_to_wgs84(x, y)
        wkt = f"POINT({lon} {lat})"
        rows.append((
            attrs.get("MNTN_NM", ""),
            attrs.get("MNTN_ID", ""),
            int(attrs.get("PMNTN_SPOT") or 0),
            attrs.get("MANAGE_SP2", ""),
            attrs.get("DETAIL_SPO", ""),
            wkt,
        ))
    return rows


def parse_trail(filepath):
    """PMNTN_*.json → [(mntn_nm, mntn_id, trail_nm, is_main, length_km, difficulty, up_min, down_min, wkt), ...]"""
    with open(filepath, encoding="utf-8") as f:
        data = json.load(f)

    # 금당산 계열은 필드 구조가 달라 등산로 데이터 아님 → 스킵
    fields = [fd["name"] for fd in data.get("fields", [])]
    if "PMNTN_LT" not in fields:
        return []

    rows = []
    for feat in data.get("features", []):
        attrs  = feat.get("attributes", {})
        paths  = feat.get("geometry", {}).get("paths", [])
        if not paths:
            continue

        # 모든 paths를 WGS84로 변환 → MULTILINESTRING
        linestrings = []
        for path in paths:
            coords = []
            for x, y in path:
                lon, lat = tm_to_wgs84(x, y)
                coords.append(f"{lon} {lat}")
            if len(coords) >= 2:
                linestrings.append(f"({', '.join(coords)})")

        if not linestrings:
            continue

        wkt = f"MULTILINESTRING({', '.join(linestrings)})"
        rows.append((
            attrs.get("MNTN_NM", ""),
            attrs.get("MNTN_ID", ""),
            attrs.get("PMNTN_NM", ""),
            attrs.get("PMNTN_MAIN", " ").strip() == "Y",
            float(attrs.get("PMNTN_LT") or 0),
            attrs.get("PMNTN_DFFL", ""),
            int(attrs.get("PMNTN_UPPL") or 0),
            int(attrs.get("PMNTN_GODN") or 0),
            wkt,
        ))
    return rows


conn = psycopg2.connect(**DB)
cur  = conn.cursor()

peak_total  = 0
trail_total = 0

for dirname in sorted(os.listdir(BASE_DIR)):
    dirpath = os.path.join(BASE_DIR, dirname)
    if not os.path.isdir(dirpath):
        continue

    for filename in os.listdir(dirpath):
        filepath = os.path.join(dirpath, filename)

        if filename.startswith("PMNTN_SPOT_") and filename.endswith(".json"):
            rows = parse_peak(filepath)
            cur.executemany(
                """INSERT INTO mountain_peak
                   (mntn_nm, mntn_id, spot_no, manage_type, detail, geometry)
                   VALUES (%s, %s, %s, %s, %s, ST_GeomFromText(%s, 4326))""",
                rows,
            )
            peak_total += len(rows)
            print(f"  봉우리 {filename}: {len(rows)}개")

        elif filename.startswith("PMNTN_") and filename.endswith(".json"):
            rows = parse_trail(filepath)
            if not rows:
                continue
            cur.executemany(
                """INSERT INTO mountain_trail
                   (mntn_nm, mntn_id, trail_nm, is_main, length_km, difficulty, up_min, down_min, geometry)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, ST_GeomFromText(%s, 4326))""",
                rows,
            )
            trail_total += len(rows)
            print(f"  등산로 {filename}: {len(rows)}개")

conn.commit()
cur.close()
conn.close()

print(f"\n완료: 위치표지 {peak_total}개, 등산로 {trail_total}개")
```

---

## 4. 적재 후 테이블 예시

### `mountain_peak`

| id | mntn_nm | mntn_id | spot_no | manage_type | detail | geometry |
|----|---------|---------|:-------:|-------------|--------|----------|
| 1 | 갈미봉 | 291100101 | 1521 | 시종점 | 시종점 | POINT(126.xxx 35.xxx) |
| 2 | 갈미봉 | 291100101 | 1522 | 갈림목 | 1코스갈림목 | POINT(126.xxx 35.xxx) |

### `mountain_trail`

| id | mntn_nm | trail_nm | is_main | length_km | difficulty | up_min | down_min | geometry |
|----|---------|----------|:-------:|:---------:|------------|:------:|:--------:|----------|
| 1 | 갈미봉 | 내남동구간 | false | 0.39 | 쉬움 | 7 | 5 | MULTILINESTRING(...) |
| 2 | 깃대봉 | 운림동구간 | false | 0.73 | 쉬움 | 18 | 13 | MULTILINESTRING(...) |

---

## 5. 공간 쿼리 예시

```sql
-- 특정 좌표 반경 1km 내 등산로 조회
SELECT mntn_nm, trail_nm, difficulty, length_km
FROM mountain_trail
WHERE ST_DWithin(
    geometry::geography,
    ST_MakePoint(126.85, 35.15)::geography,
    1000
);

-- 난이도별 등산로 통계
SELECT difficulty, COUNT(*) AS 개수, ROUND(SUM(length_km)::numeric, 2) AS 총길이_km
FROM mountain_trail
GROUP BY difficulty;

-- 산별 위치표지 수
SELECT mntn_nm, COUNT(*) AS 위치표지수
FROM mountain_peak
GROUP BY mntn_nm
ORDER BY 위치표지수 DESC;
```

---

## 6. 주의사항

- **금당산(291550301), 금당산숲길(291551504)** 의 `PMNTN_*.json`은 등산로가 아닌
  안전구역 정보(`SAFE_SPOT1~3`, `MGC`)를 담고 있어 적재 스크립트에서 자동으로 스킵됩니다.
- `paths`가 여러 개인 경우 모든 경로를 `MULTILINESTRING`으로 묶어 저장합니다.
