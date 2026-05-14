#!/usr/bin/env bash
set -euo pipefail

TILESERVER_DIR="${TILESERVER_DIR:-/home/ubuntu/infra/tileserver}"
SOURCE_DIR="${1:-${GWANGJU_TOPO_SOURCE_DIR:-${TILESERVER_DIR}/source/gwangju-continuous-topo}}"
OUTPUT_DIR="${2:-${TILESERVER_DIR}/data}"
WORK_DIR="${WORK_DIR:-/tmp/suri-map-gwangju-building-labels}"
GDAL_IMAGE="${GDAL_IMAGE:-ghcr.io/osgeo/gdal:ubuntu-small-latest}"
TIPPECANOE_IMAGE="${TIPPECANOE_IMAGE:-ghcr.io/felt/tippecanoe:latest}"
BUILDING_LAYER_GLOB="${GWANGJU_BUILDING_LAYER_GLOB:-N3A_B0010000.shp}"

mkdir -p "$OUTPUT_DIR" "$WORK_DIR"
rm -f "$WORK_DIR"/*.geojsonseq

if ! find "$SOURCE_DIR" -mindepth 2 -maxdepth 2 -name "$BUILDING_LAYER_GLOB" | grep -q .; then
  echo "$BUILDING_LAYER_GLOB not found under $SOURCE_DIR" >&2
  exit 1
fi

docker run --rm \
  -e BUILDING_LAYER_GLOB="$BUILDING_LAYER_GLOB" \
  -v "$SOURCE_DIR:/source:ro" \
  -v "$WORK_DIR:/work" \
  "$GDAL_IMAGE" \
  bash -lc '
    set -euo pipefail
    : > /work/gwangju-building-labels.geojsonseq
    while IFS= read -r -d "" shp; do
      district="$(basename "$(dirname "$shp")")"
      layer="$(basename "$shp" .shp)"
      out="/work/${district}.geojsonseq"
      ogr2ogr \
        -f GeoJSONSeq "$out" "$shp" \
        -oo ENCODING=CP949 \
        -s_srs EPSG:5179 \
        -t_srs EPSG:4326 \
        -dialect SQLite \
        -sql "SELECT ST_PointOnSurface(geometry) AS geometry, NAME AS name, UFID AS ufid, NMLY AS floor_count, '\''${district}'\'' AS district FROM \"${layer}\" WHERE NAME IS NOT NULL AND TRIM(NAME) <> '\'''\''"
      cat "$out" >> /work/gwangju-building-labels.geojsonseq
    done < <(find /source -mindepth 2 -maxdepth 2 -name "$BUILDING_LAYER_GLOB" -print0 | sort -z)
  '

docker run --rm \
  -v "$WORK_DIR:/work:ro" \
  -v "$OUTPUT_DIR:/out" \
  --entrypoint tippecanoe \
  "$TIPPECANOE_IMAGE" \
  --output=/out/gwangju-building-labels.mbtiles \
  --layer=building_labels \
  --minimum-zoom=16 \
  --maximum-zoom=16 \
  --force \
  --drop-densest-as-needed \
  --extend-zooms-if-still-dropping \
  /work/gwangju-building-labels.geojsonseq

ls -lh "$OUTPUT_DIR/gwangju-building-labels.mbtiles"
