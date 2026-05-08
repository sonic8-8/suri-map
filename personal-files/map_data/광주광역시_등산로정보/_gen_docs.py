import sys, os
sys.stdout.reconfigure(encoding='utf-8')

BASE = r'C:\Users\SSAFY\Desktop\등산로정보'

# ── 데이터정의서 ─────────────────────────────────────────────
definition = '''# 등산로정보 데이터 정의서

## 개요

- **출처**: 산림청 (국가공간정보포털)
- **대상 지역**: 광주광역시
- **포맷**: Esri JSON (ArcGIS REST API 형식, 표준 GeoJSON과 다름)
- **좌표계**: EPSG:5186 (PCS_ITRF2000_TM) → 적재 시 EPSG:4326으로 변환 필요
- **디렉토리 구조**: `{산코드}_geojson/` 하위에 봉우리·등산로 파일 존재

```
광주광역시/
├── 291100101_geojson/    ← 갈미봉
│   ├── PMNTN_SPOT_갈미봉_291100101.json   ← 위치표지 포인트
│   └── PMNTN_갈미봉_291100101.json        ← 등산로 폴리라인
├── 291100401_geojson/    ← 깃대봉
│   └── ...
└── ...
```

---

## 산 목록 (10개)

| 산코드 | 산 이름 | 위치표지 수 | 등산로 수 |
|--------|---------|:---------:|:-------:|
| 291100101 | 갈미봉 | 8 | 8 |
| 291100401 | 깃대봉 | 104 | 56 |
| 291101901 | 마집봉 | 177 | 56 |
| 291102001 | 구와산 | 19 | 24 |
| 291400101 | 개금산 | 21 | 3 |
| 291550301 | 금당산 | 135 | 5 |
| 291551301 | 방구산 | 2 | 1 |
| 291551504 | 금당산숲길 | 100 | 8 |
| 291701701 | 군왕봉 | 16 | 16 |
| 291701801 | 덕봉 | 8 | 8 |

---

## 파일 타입

### PMNTN_SPOT — 위치표지 포인트

지형별 위치 표지판 (시종점, 갈림목, 위험지점 등)

- **geometryType**: `esriGeometryPoint`
- **geometry 구조**: `{ "x": 192904.6, "y": 277428.9 }` (EPSG:5186 TM 좌표)

| 필드명 | 타입 | 설명 | 예시 |
|--------|------|------|------|
| `FID` | Integer | 객체 ID | `0` |
| `PMNTN_SPOT` | Double | 위치표지 번호 | `1521` |
| `MNTN_CODE` | String | 산 코드 | `291100101` |
| `MANAGE_SP1` | String | 관리구분코드 | `01` |
| `MANAGE_SP2` | String | 관리구분명 | `시종점` |
| `DETAIL_SPO` | String | 상세위치 설명 | `시종점` |
| `ETC_MATTER` | String | 기타사항 | - |
| `MNTN_NM` | String | 산 이름 | `갈미봉` |
| `PAST_SPOT_` | String | 이전 포인트 코드 | `2911001010001` |
| `MNTN_ID` | String | 산 ID | `291100101` |

---

### PMNTN — 등산로 폴리라인

- **geometryType**: `esriGeometryPolyline`
- **geometry 구조**: `{ "paths": [[[x1,y1],[x2,y2],...]] }` (EPSG:5186 TM 좌표)

> **주의**: 금당산(291550301), 금당산숲길(291551504)의 PMNTN 파일은 필드 구조가 다릅니다.
> 일반 등산로 데이터가 아닌 안전구역 정보(`SAFE_SPOT1~3`, `MGC`)를 담고 있습니다.

#### 일반 등산로 필드 (8개 산)

| 필드명 | 타입 | 설명 | 예시 |
|--------|------|------|------|
| `PMNTN_SN` | Integer | 등산로 일련번호 | `922` |
| `MNTN_CODE` | String | 산 코드 | `291100101` |
| `MNTN_NM` | String | 산 이름 | `갈미봉` |
| `PMNTN_NM` | String | 등산로 구간명 | `내남동구간` |
| `PMNTN_MAIN` | String | 주요등산로 여부 | `Y` / ` ` |
| `PMNTN_LT` | Double | 구간 길이(km) | `0.39` |
| `PMNTN_DFFL` | String | 난이도 | `쉬움` / `보통` / `어려움` |
| `PMNTN_UPPL` | Integer | 오르막 소요시간(분) | `7` |
| `PMNTN_GODN` | Integer | 내리막 소요시간(분) | `5` |
| `PMNTN_MTRQ` | String | 이용자격 제한 | - |
| `PMNTN_CNRL` | String | 통제 여부 | - |
| `PMNTN_CLS_` | String | 폐쇄 여부 | - |
| `PMNTN_RISK` | String | 위험 등급 | - |
| `PMNTN_RECO` | String | 추천 여부 | - |
| `DATA_STDR_` | String | 데이터 기준일 | `2016-12-31` |
| `MNTN_ID` | String | 산 ID | `291100101` |

---

## PostGIS 테이블 구조

### `mountain_peak` (위치표지 포인트)

```sql
CREATE TABLE mountain_peak (
    id          SERIAL PRIMARY KEY,
    mntn_nm     VARCHAR(100),   -- 산 이름
    mntn_id     VARCHAR(20),    -- 산 ID
    spot_no     INTEGER,        -- 위치표지 번호
    manage_type VARCHAR(50),    -- 관리구분명 (시종점, 갈림목 등)
    detail      VARCHAR(200),   -- 상세위치 설명
    geometry    GEOMETRY(POINT, 4326)
);
```

### `mountain_trail` (등산로 폴리라인)

```sql
CREATE TABLE mountain_trail (
    id          SERIAL PRIMARY KEY,
    mntn_nm     VARCHAR(100),   -- 산 이름
    mntn_id     VARCHAR(20),    -- 산 ID
    trail_nm    VARCHAR(200),   -- 등산로 구간명
    is_main     BOOLEAN,        -- 주요 등산로 여부
    length_km   FLOAT,          -- 구간 길이(km)
    difficulty  VARCHAR(20),    -- 난이도 (쉬움/보통/어려움)
    up_min      INTEGER,        -- 오르막 소요시간(분)
    down_min    INTEGER,        -- 내리막 소요시간(분)
    geometry    GEOMETRY(MULTILINESTRING, 4326)
);
```
'''

with open(os.path.join(BASE, '데이터정의서.md'), 'w', encoding='utf-8') as f:
    f.write(definition)
print('데이터정의서.md 저장 완료')


# ── 적재 가이드 ──────────────────────────────────────────────
guide = '''# 등산로정보 → PostGIS 적재 가이드

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

BASE_DIR = r"C:\\Users\\SSAFY\\Desktop\\등산로정보\\광주광역시"

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

print(f"\\n완료: 위치표지 {peak_total}개, 등산로 {trail_total}개")
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
'''

with open(os.path.join(BASE, 'PostGIS_적재_가이드.md'), 'w', encoding='utf-8') as f:
    f.write(guide)
print('PostGIS_적재_가이드.md 저장 완료')
