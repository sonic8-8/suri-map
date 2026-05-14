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
│  └─ Noto Sans CJK KR Regular/
│     ├─ 0-255.pbf
│     └─ ...
└─ styles/
   └─ osm-local/
      └─ style.json
```

`osm-local.mbtiles` is not committed because real map data is large and
environment-specific. Use an OpenMapTiles-compatible MBTiles file whose layer
names match `styles/osm-local/style.json`.

`gwangju-building-labels.mbtiles` is a Gwangju-only label overlay. It contains
one vector layer:

```text
building_labels
```

Each feature should be a point with at least `name`. Optional properties used
for debugging and future styling are `ufid`, `district`, and `floor_count`.
Generate it from the Gwangju continuous digital topographic map building layer.
The recommended EC2 source directory is:

```text
/home/ubuntu/infra/tileserver/source/gwangju-continuous-topo
```

By default the script searches each district directory for `N3A_B0010000.shp`:

```bash
bash infra/docker/tileserver/scripts/build-gwangju-building-labels.sh \
  /home/ubuntu/infra/tileserver/source/gwangju-continuous-topo \
  /home/ubuntu/infra/tileserver/data
```

If the source shapefile bundle is renamed, keep the `.shp`, `.dbf`, `.shx`, and
`.prj` base filenames identical and set the layer glob explicitly:

```bash
GWANGJU_BUILDING_LAYER_GLOB=building-labels.shp \
bash infra/docker/tileserver/scripts/build-gwangju-building-labels.sh \
  /home/ubuntu/infra/tileserver/source/gwangju-continuous-topo \
  /home/ubuntu/infra/tileserver/data
```

The style renders this overlay from zoom 16. MapLibre text rendering also needs
local glyph PBF files. Put Korean-capable glyphs under the font stack directory
referenced by `style.json`, currently:

```text
/home/ubuntu/infra/tileserver/fonts/Noto Sans CJK KR Regular/
```

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
