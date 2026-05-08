from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import geopandas as gpd
import pandas as pd


ROOT = Path(__file__).resolve().parents[2]
MAP_ROOT = ROOT / "personal-files" / "map_data"
SOURCE_ROOT = MAP_ROOT / "광주광역시_연속수치지형도" / "광산구"
PUBLIC_OUT = ROOT / "frontend" / "public" / "map-data" / "gwangsan"
ARCHIVE_OUT = MAP_ROOT / "processed" / "gwangsan"
SOURCE_CRS = "EPSG:5179"
TARGET_CRS = "EPSG:4326"
BOUNDARY_SOURCE_CODE = "N3A_G0100000"
BOUNDARY_NAME = "광산구"


LAYER_CONFIGS = {
    "boundary": {
        "sources": [BOUNDARY_SOURCE_CODE],
        "semanticType": "boundary",
        "simplifyMeters": 1.0,
        "filterName": BOUNDARY_NAME,
    },
}


def read_source(source_code: str) -> gpd.GeoDataFrame:
    path = SOURCE_ROOT / f"{source_code}.shp"
    frame = gpd.read_file(path)
    if frame.crs is None:
        frame = frame.set_crs(SOURCE_CRS)
    return frame


def compact_properties(row: Any, layer_id: str, semantic_type: str, source_code: str) -> dict[str, str]:
    properties = {
        "layerId": layer_id,
        "semanticType": semantic_type,
        "sourceCode": source_code,
        "sourceUfid": str(row.get("UFID", "")),
    }
    name = row.get("NAME")
    if name is not None and not pd.isna(name) and str(name).strip():
        properties["name"] = str(name)
    return properties


def load_boundary_mask() -> gpd.GeoDataFrame:
    boundary = read_source(BOUNDARY_SOURCE_CODE)
    boundary = boundary[boundary["NAME"] == BOUNDARY_NAME].copy()
    boundary["geometry"] = boundary.geometry.buffer(0)
    return boundary[["geometry"]]


def round_coordinates(value: Any) -> Any:
    if isinstance(value, float):
        return round(value, 6)
    if isinstance(value, list):
        return [round_coordinates(item) for item in value]
    if isinstance(value, dict):
        return {key: round_coordinates(item) for key, item in value.items()}
    return value


def write_geojson(layer_id: str, config: dict[str, Any], boundary_mask: gpd.GeoDataFrame) -> dict[str, Any]:
    frames: list[gpd.GeoDataFrame] = []
    for source_code in config["sources"]:
        frame = read_source(source_code)
        if config.get("filterName") and "NAME" in frame.columns:
            frame = frame[frame["NAME"] == config["filterName"]]
        if config.get("majorRoadsOnly"):
            road_kind = frame["RDDV"].fillna("")
            road_lanes = pd.to_numeric(frame["RDLN"], errors="coerce").fillna(0)
            frame = frame[(road_kind != "RDD000") | (road_lanes >= 2)]
        if config.get("minAreaSquareMeters"):
            frame = frame[frame.geometry.area >= float(config["minAreaSquareMeters"])]

        frame = frame[frame.geometry.notna()].copy()
        if layer_id != "boundary":
            frame = gpd.clip(frame, boundary_mask)
            frame = frame[frame.geometry.notna()].copy()
            if config.get("minAreaSquareMeters"):
                frame = frame[frame.geometry.area >= float(config["minAreaSquareMeters"])]

        frame["geometry"] = frame.geometry.simplify(
            float(config["simplifyMeters"]),
            preserve_topology=True,
        )
        frame = frame[~frame.geometry.is_empty].copy()
        frame["__sourceCode"] = source_code
        frames.append(frame)

    merged = gpd.GeoDataFrame(
        pd.concat(frames, ignore_index=True),
        crs=frames[0].crs,
    )

    if config.get("dissolve"):
        merged = merged.dissolve(by="__sourceCode", as_index=False)

    merged = merged.to_crs(TARGET_CRS)

    features = []
    for _, row in merged.iterrows():
        geometry = row.geometry.__geo_interface__
        features.append(
            {
                "type": "Feature",
                "properties": compact_properties(
                    row,
                    layer_id,
                    str(config["semanticType"]),
                    str(row["__sourceCode"]),
                ),
                "geometry": geometry,
            }
        )

    collection = round_coordinates({"type": "FeatureCollection", "features": features})
    bbox = [round(value, 6) for value in merged.total_bounds.tolist()]
    file_name = f"{layer_id}.geojson"

    for output_dir in (PUBLIC_OUT, ARCHIVE_OUT):
        output_dir.mkdir(parents=True, exist_ok=True)
        with (output_dir / file_name).open("w", encoding="utf-8") as output:
            json.dump(collection, output, ensure_ascii=False, separators=(",", ":"))

    return {
        "layerId": layer_id,
        "url": f"/map-data/gwangsan/{file_name}",
        "sourceCodes": config["sources"],
        "crs": TARGET_CRS,
        "bbox": bbox,
        "featureCount": len(features),
    }


def main() -> None:
    boundary_mask = load_boundary_mask()
    layers = [write_geojson(layer_id, config, boundary_mask) for layer_id, config in LAYER_CONFIGS.items()]
    manifest = {
        "id": "gwangsan-static-map-v1",
        "name": "Gwangsan-gu static MapLibre layers",
        "crs": TARGET_CRS,
        "layers": layers,
    }

    for output_dir in (PUBLIC_OUT, ARCHIVE_OUT):
        with (output_dir / "manifest.json").open("w", encoding="utf-8") as output:
            json.dump(manifest, output, ensure_ascii=False, indent=2)
            output.write("\n")


if __name__ == "__main__":
    main()
