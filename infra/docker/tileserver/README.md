# TileServer GL Runtime Data

This directory is the checked-in template for the EC2 tileserver data directory.
Jenkins copies `config.json` and `styles/osm-local/style.json` from this
directory into the runtime path during deploy. The real `osm-local.mbtiles`
file must be placed there separately because it is large and environment-specific.

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
│  └─ osm-local.mbtiles
└─ styles/
   └─ osm-local/
      └─ style.json
```

`osm-local.mbtiles` is not committed because real map data is large and
environment-specific. Use an OpenMapTiles-compatible MBTiles file whose layer
names match `styles/osm-local/style.json`.

The public Suri-Map tile contract remains:

```text
GET /tiles/styles/osm-local.json
GET /tiles/osm-local/{z}/{x}/{y}.pbf
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
```

`frontend/nginx.conf` carries a container-level fallback that forwards `/tiles`
to the backend when host nginx sends tile requests to the frontend container.
