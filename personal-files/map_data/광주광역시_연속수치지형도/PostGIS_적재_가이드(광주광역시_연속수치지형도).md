# 연속수치지형도 → PostGIS 적재 가이드

## 1. 환경 설치

```bash
pip install geopandas sqlalchemy psycopg2-binary
```

> geopandas 설치 시 의존성(GDAL, Fiona, Shapely)이 함께 설치됩니다.
> Windows에서 오류가 날 경우 conda 사용 권장:
> ```bash
> conda install -c conda-forge geopandas sqlalchemy psycopg2
> ```

---

## 2. PostGIS 확장 활성화

PostgreSQL에 PostGIS가 설치되어 있어야 합니다.

```sql
CREATE DATABASE surimap;
\c surimap
CREATE EXTENSION postgis;
```

---

## 3. 전체 적재 스크립트

5개 구 × 64개 레이어를 한 번에 적재합니다.
레이어 코드를 소문자로 변환하여 테이블명으로 사용합니다 (예: `N3A_B0010000` → `n3a_b0010000`).

```python
import geopandas as gpd
import os
from sqlalchemy import create_engine, text

BASE_DIR = r'C:\Users\SSAFY\Desktop\연속수치지형도\광주광역시'
DISTRICTS = ['광산구', '남구', '동구', '북구', '서구']

DB_URL = 'postgresql://user:password@localhost:5432/surimap'
engine = create_engine(DB_URL)

for district in DISTRICTS:
    dist_path = os.path.join(BASE_DIR, district)
    shp_files = [f for f in os.listdir(dist_path) if f.endswith('.shp')]

    for shp_file in sorted(shp_files):
        table_name = shp_file.replace('.shp', '').lower()
        shp_path = os.path.join(dist_path, shp_file)

        try:
            gdf = gpd.read_file(shp_path, encoding='cp949')

            # .prj 파일이 있어도 CRS 누락되는 경우 대비
            if gdf.crs is None:
                gdf = gdf.set_crs('EPSG:5179')

            gdf = gdf.to_crs('EPSG:4326')
            gdf = gdf.drop(columns=['Shape_Leng', 'Shape_Area'], errors='ignore')

            # 첫 번째 구는 replace, 이후 구는 append
            exists_mode = 'replace' if district == DISTRICTS[0] else 'append'
            gdf.to_postgis(table_name, engine, if_exists=exists_mode, index=False)

            print(f'[{district}] {table_name}: {len(gdf):,}개 적재')

        except Exception as e:
            print(f'[{district}] {shp_file} 오류: {e}')

# GIST 인덱스 일괄 생성
with engine.connect() as conn:
    for shp_file in sorted(os.listdir(os.path.join(BASE_DIR, DISTRICTS[0]))):
        if not shp_file.endswith('.shp'):
            continue
        table_name = shp_file.replace('.shp', '').lower()
        conn.execute(text(
            f'CREATE INDEX IF NOT EXISTS {table_name}_geom_idx '
            f'ON {table_name} USING GIST (geometry);'
        ))
    conn.commit()

print('완료')
```

---

## 4. 적재 후 테이블 구조

레이어별로 테이블이 생성되며, 공통 컬럼 구조는 다음과 같습니다.

### 공통 컬럼

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `ufid` | TEXT | 객체 고유 식별자 |
| `scls` | TEXT | 세부 레이어 코드 |
| `fmta` | TEXT | 제작 이력 코드 |
| `geometry` | GEOMETRY | 공간 데이터 (EPSG:4326) |

### 레이어별 추가 컬럼 및 데이터 예시

#### `n3a_b0010000` — 건물면

| 컬럼 | 설명 | 예시값 |
|------|------|--------|
| `ufid` | 고유ID | `B0010000000S6P9LT` |
| `bjcd` | 법정동코드 | `2920013500` |
| `name` | 건물명 | `광주시청` |
| `divi` | 구분코드 | `BDD001` |
| `kind` | 건물종류 | `BDK008` |
| `serv` | 서비스코드 | `BDS999` |
| `nmly` | 층수 | `5` |
| `rdnm` | 도로명주소 | `상무대로 30` |
| `bonu` | 건물번호(본) | `30` |
| `bunu` | 건물번호(부) | `0` |
| `post` | 우편번호 | `61948` |
| `scls` | 세부코드 | `B0014118` |
| `geometry` | Polygon | `POLYGON ((126.748 35.250, ...))` |

```sql
SELECT name, nmly, rdnm, ST_Area(geometry::geography) AS area_m2
FROM n3a_b0010000
WHERE name IS NOT NULL
LIMIT 5;
```

#### `n3l_a0020000` — 도로중심선

| 컬럼 | 설명 | 예시값 |
|------|------|--------|
| `ufid` | 고유ID | `A002000000002D6OL` |
| `rdnu` | 도로번호 | `17` |
| `name` | 도로명 | `상무대로` |
| `rddv` | 도로구분 | `RDD009` (일반도로) |
| `pvqt` | 포장상태 | `RDQ005` (아스팔트) |
| `rdln` | 차로수 | `4` |
| `rvwd` | 도로폭(m) | `20.0` |
| `onsd` | 일방통행 | `ITH002` (양방향) |
| `rdnm` | 도로명 | `상무대로` |
| `scls` | 세부코드 | `A0023119` |
| `geometry` | LineString | `LINESTRING (126.749 35.252, ...)` |

```sql
SELECT name, rdln AS 차로수, rvwd AS 도로폭_m, ST_Length(geometry::geography) AS 길이_m
FROM n3l_a0020000
WHERE name IS NOT NULL
ORDER BY 길이_m DESC
LIMIT 5;
```

#### `n3a_d0010000` — 하천면

| 컬럼 | 설명 | 예시값 |
|------|------|--------|
| `ufid` | 고유ID | `100035615057D00110100000000013305` |
| `divi` | 구분코드 | `FLD002` |
| `scls` | 세부코드 | `D0015212` |
| `geometry` | Polygon | `POLYGON ((126.660 35.124, ...))` |

#### `n3p_c0285311` — 버스정류장점

| 컬럼 | 설명 | 예시값 |
|------|------|--------|
| `ufid` | 고유ID | `100035616071C02810100000000293547` |
| `bjcd` | 법정동코드 | `2920014100` |
| `scls` | 세부코드 | `C0285311` |
| `geometry` | Point | `POINT (126.772 35.074)` |

---

## 5. 적재 후 레코드 수 확인

```sql
-- 전체 테이블 레코드 수 조회
SELECT
    relname AS 테이블명,
    n_live_tup AS 레코드수
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;
```

예상 레코드 수 (5개 구 합산 기준):

| 테이블 | 의미 | 예상 레코드수 |
|--------|------|:------------:|
| `n3p_c0285311` | 버스정류장점 | ~200,000 |
| `n3a_b0010000` | 건물면 | ~500,000 |
| `n3a_d0010000` | 하천면 | ~200,000 |
| `n3l_a0020000` | 도로중심선 | ~300,000 |
| `n3p_c0310000` | 가로등점 | ~100,000 |

---

## 6. 공간 쿼리 예시

```sql
-- 특정 좌표 반경 500m 내 건물 조회
SELECT name, rdnm
FROM n3a_b0010000
WHERE ST_DWithin(
    geometry::geography,
    ST_MakePoint(126.85, 35.15)::geography,
    500
);

-- 특정 구역과 교차하는 도로 조회
SELECT name, rdln, rvwd
FROM n3l_a0020000
WHERE ST_Intersects(
    geometry,
    ST_MakeEnvelope(126.84, 35.14, 126.86, 35.16, 4326)
);
```
