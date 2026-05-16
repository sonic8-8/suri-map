# TileServer GL Runtime Data

This directory is the checked-in template for the EC2 tileserver data directory.
Jenkins copies `config.json` and `styles/osm-local/style.json` from this
directory into the runtime path during deploy. Real `.mbtiles` map data and
glyph PBF files must be placed there separately because they are large and
environment-specific.

Runtime compose mounts `TILESERVER_DATA_DIR` to `/data` in the `tileserver-gl`
container. The EC2 runtime path is:

```text
/home/ubuntu/infra/tileserver
```

Prepare the EC2 directory with this shape:

```text
/home/ubuntu/infra/tileserver/
├─ config.json
├─ data/
│  ├─ osm-local.mbtiles
│  └─ gwangju-building-labels.mbtiles
├─ fonts/
│  └─ Pretendard GOV/
│     ├─ 0-255.pbf
│     └─ ...
└─ styles/
   └─ osm-local/
      └─ style.json
```

`osm-local.mbtiles` is not committed because real map data is large and
environment-specific. For the Gwangju runtime, generate it from the Gwangju
continuous digital topographic map so low zooms can show the full city while
zoom 16 still carries detailed building geometry:

```bash
bash infra/docker/tileserver/scripts/build-gwangju-osm-local.sh \
  /home/ubuntu/infra/tileserver/source/gwangju-continuous-topo/광주광역시_연속수치지형도 \
  /home/ubuntu/infra/tileserver/data
```

The generated file uses the source-layer names consumed by
`styles/osm-local/style.json`:

```text
landcover       N3A_G0100000 administrative district polygons
boundary        N3A_G0100000 administrative district polygon outlines
water           N3A_E0010001, N3A_E0032111, N3A_E0052114 water polygons
transportation  N3L_A0020000 named or multi-lane road centerlines
building        N3A_B0010000 building polygons
```

Base layers are built for zooms 8 through 16. Building polygons are added from
zoom 15 through 16 so city-level tiles stay small enough for Android. Do not
replace this with a z16-only MBTiles file; Android needs lower zoom tiles when
fitting the full Gwangju verification viewport.

`gwangju-building-labels.mbtiles` is a Gwangju-only label overlay. It contains
one vector layer:

```text
building_labels
```

Each feature should be a point with at least `name`. Optional properties used
for debugging and future styling are `ufid`, `district`, and `floor_count`.
Generate it from the Gwangju continuous digital topographic map building layer
`N3A_B0010000` with:

```bash
bash infra/docker/tileserver/scripts/build-gwangju-building-labels.sh \
  /home/ubuntu/infra/tileserver/source/gwangju-continuous-topo/광주광역시_연속수치지형도 \
  /home/ubuntu/infra/tileserver/data
```

The style renders this overlay from zoom 16. MapLibre text rendering also needs
local glyph PBF files. Put Korean-capable glyphs under the font stack directory
referenced by `style.json`, currently:

```text
/home/ubuntu/infra/tileserver/fonts/Pretendard GOV/
```

The application UI uses the same government design-system family through the
Android resource `android/app/src/main/res/font/pretendard_gov_variable.ttf`.
That APK font does not satisfy MapLibre labels by itself; TileServer GL still
needs matching glyph PBF files under the `Pretendard GOV` font stack.

Generate the runtime glyph PBF files from the checked-in Pretendard GOV TTF
with:

```bash
bash infra/docker/tileserver/scripts/build-pretendard-gov-glyphs.sh \
  android/app/src/main/res/font/pretendard_gov_variable.ttf \
  "/home/ubuntu/infra/tileserver/fonts/Pretendard GOV"
```

Jenkins runs this script during deploy before starting `tileserver-gl`. Local
Android smoke also runs it when the runtime `Pretendard GOV/0-255.pbf` file is
missing.

The public Suri-Map tile contract remains:

```text
GET /tiles/styles/osm-local.json
GET /tiles/osm-local/{z}/{x}/{y}.pbf
GET /tiles/gwangju-building-labels/{z}/{x}/{y}.pbf
GET /tiles/fonts/{fontStack}/{range}.pbf
```

In production, the EC2 host nginx should proxy the public `/tiles` paths to the
authenticated backend port (`127.0.0.1:8081`). The backend validates the public
APP/WEB session and then fetches native TileServer GL paths from
`http://tileserver-gl:8080` on the Docker network. Use
`infra/nginx/tileserver-gl.locations.example.conf` as the location template.

The backend rewrites public paths to TileServer GL native paths:

```text
/tiles/styles/osm-local.json       -> /styles/osm-local/style.json
/tiles/osm-local/{z}/{x}/{y}.pbf   -> /data/osm-local/{z}/{x}/{y}.pbf
/tiles/gwangju-building-labels/{z}/{x}/{y}.pbf
                                    -> /data/gwangju-building-labels/{z}/{x}/{y}.pbf
/tiles/fonts/{fontStack}/{range}.pbf
                                    -> /fonts/{fontStack}/{range}.pbf
```

`frontend/nginx.conf` carries a container-level fallback that forwards `/tiles`
to the backend when host nginx sends tile requests to the frontend container.
