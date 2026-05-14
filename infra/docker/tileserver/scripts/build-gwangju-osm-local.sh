#!/usr/bin/env bash
set -euo pipefail

TILESERVER_DIR="${TILESERVER_DIR:-/home/ubuntu/infra/tileserver}"
SOURCE_DIR="${1:-${GWANGJU_TOPO_SOURCE_DIR:-${TILESERVER_DIR}/source/gwangju-continuous-topo/광주광역시_연속수치지형도}}"
OUTPUT_DIR="${2:-${TILESERVER_DIR}/data}"
WORK_DIR="${WORK_DIR:-/tmp/suri-map-gwangju-osm-local}"
GDAL_IMAGE="${GDAL_IMAGE:-ghcr.io/osgeo/gdal:ubuntu-small-latest}"
TIPPECANOE_IMAGE="${TIPPECANOE_IMAGE:-morlov/tippecanoe:latest}"

mkdir -p "$OUTPUT_DIR" "$WORK_DIR"
rm -f "$WORK_DIR"/*.geojsonseq "$WORK_DIR"/*.tmp.geojsonseq

require_layer() {
  local layer="$1"
  if ! find "$SOURCE_DIR" -mindepth 2 -maxdepth 2 -name "${layer}.shp" | grep -q .; then
    echo "${layer}.shp not found under $SOURCE_DIR" >&2
    exit 1
  fi
}

require_layer N3A_B0010000
require_layer N3A_G0100000
require_layer N3L_A0020000

docker run --rm \
  -v "$SOURCE_DIR:/source:ro" \
  -v "$WORK_DIR:/work" \
  "$GDAL_IMAGE" \
  bash -lc '
    set -euo pipefail

    : > /work/building.geojsonseq
    : > /work/transportation.geojsonseq
    : > /work/water.geojsonseq
    : > /work/landcover.tmp.geojsonseq
    : > /work/boundary.tmp.geojsonseq

    for shp in /source/*/N3A_G0100000.shp; do
      district="$(basename "$(dirname "$shp")")"
      out="/work/landcover-${district}.geojsonseq"
      ogr2ogr \
        -f GeoJSONSeq "$out" "$shp" \
        -oo ENCODING=CP949 \
        -s_srs EPSG:5179 \
        -t_srs EPSG:4326 \
        -dialect SQLite \
        -sql "SELECT geometry, UFID AS ufid, BJCD AS code, NAME AS name, DIVI AS admin_level, '\''${district}'\'' AS source_district FROM N3A_G0100000 WHERE BJCD LIKE '\''29%'\''"
      cat "$out" >> /work/landcover.tmp.geojsonseq
      cat "$out" >> /work/boundary.tmp.geojsonseq
    done

    awk '\''
      match($0, /"ufid":"[^"]+"/) {
        key=substr($0, RSTART, RLENGTH)
        if (!seen[key]++) print
      }
    '\'' /work/landcover.tmp.geojsonseq > /work/landcover.geojsonseq

    awk '\''
      match($0, /"ufid":"[^"]+"/) {
        key=substr($0, RSTART, RLENGTH)
        if (!seen[key]++) print
      }
    '\'' /work/boundary.tmp.geojsonseq > /work/boundary.geojsonseq

    for shp in /source/*/N3A_B0010000.shp; do
      district="$(basename "$(dirname "$shp")")"
      out="/work/building-${district}.geojsonseq"
      ogr2ogr \
        -f GeoJSONSeq "$out" "$shp" \
        -oo ENCODING=CP949 \
        -s_srs EPSG:5179 \
        -t_srs EPSG:4326 \
        -dialect SQLite \
        -sql "SELECT geometry, UFID AS ufid, NAME AS name, NMLY AS floor_count, BJCD AS code, '\''${district}'\'' AS district FROM N3A_B0010000"
      cat "$out" >> /work/building.geojsonseq
    done

    for shp in /source/*/N3L_A0020000.shp; do
      district="$(basename "$(dirname "$shp")")"
      out="/work/transportation-${district}.geojsonseq"
      ogr2ogr \
        -f GeoJSONSeq "$out" "$shp" \
        -oo ENCODING=CP949 \
        -s_srs EPSG:5179 \
        -t_srs EPSG:4326 \
        -dialect SQLite \
        -sql "SELECT geometry, UFID AS ufid, NAME AS name, RDDV AS road_type, RDLN AS lanes, SCLS AS class, '\''${district}'\'' AS district FROM N3L_A0020000 WHERE NAME IS NOT NULL OR RDLN >= 2"
      cat "$out" >> /work/transportation.geojsonseq
    done

    for layer in N3A_E0010001 N3A_E0032111 N3A_E0052114; do
      for shp in /source/*/${layer}.shp; do
        [ -f "$shp" ] || continue
        district="$(basename "$(dirname "$shp")")"
        out="/work/water-${layer}-${district}.geojsonseq"
        name_expr="NULL AS name"
        if [ "$layer" = "N3A_E0052114" ]; then
          name_expr="NAME AS name"
        fi
        ogr2ogr \
          -f GeoJSONSeq "$out" "$shp" \
          -oo ENCODING=CP949 \
          -s_srs EPSG:5179 \
          -t_srs EPSG:4326 \
          -dialect SQLite \
          -sql "SELECT geometry, UFID AS ufid, ${name_expr}, SCLS AS class, '\''${layer}'\'' AS source_layer, '\''${district}'\'' AS district FROM ${layer}"
        cat "$out" >> /work/water.geojsonseq
      done
    done
  '

docker run --rm \
  -v "$WORK_DIR:/work" \
  -v "$OUTPUT_DIR:/out" \
  --entrypoint sh \
  "$TIPPECANOE_IMAGE" \
  -lc '
    set -eu

    tippecanoe \
      --output=/work/osm-local-base.mbtiles \
      --name="Suri-Map Gwangju Topographic Base" \
      --attribution="Gwangju continuous digital topographic map" \
      --minimum-zoom=8 \
      --maximum-zoom=16 \
      --force \
      --detect-shared-borders \
      --drop-densest-as-needed \
      --extend-zooms-if-still-dropping \
      --named-layer=landcover:/work/landcover.geojsonseq \
      --named-layer=water:/work/water.geojsonseq \
      --named-layer=boundary:/work/boundary.geojsonseq \
      --named-layer=transportation:/work/transportation.geojsonseq

    tippecanoe \
      --output=/work/osm-local-buildings.mbtiles \
      --layer=building \
      --minimum-zoom=15 \
      --maximum-zoom=16 \
      --force \
      --drop-densest-as-needed \
      --extend-zooms-if-still-dropping \
      /work/building.geojsonseq

    tile-join \
      -f \
      -o /out/osm-local.mbtiles \
      /work/osm-local-base.mbtiles \
      /work/osm-local-buildings.mbtiles
  '

ls -lh "$OUTPUT_DIR/osm-local.mbtiles"
