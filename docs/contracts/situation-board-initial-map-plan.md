# Situation Board Initial Map Loading Plan

## Decision

Situation board initial map loading must not assume that `overallSearchArea` already exists when an incident is first imported.

For normal mock/seed incident import, the UI may treat initial reference markers as the expected first map anchor. The implementation must still handle an empty marker set defensively.

Initial viewport priority:

1. Active `overallSearchArea` bbox/geometry.
2. Initial/reference marker points.
3. Jurisdiction/default map fallback.

## Document Basis

- `GET /api/incidents/{incidentId}` returns incident metadata, missing person data, and incident assignments only. It does not include search area geometry.
- Active overall search area is queried separately with:

```http
GET /api/search-areas?incidentId={incidentId}&areaLevel=OVERALL&status=ACTIVE
```

- Missing person data has `last_seen_location_text` and `last_seen_at`, but no coordinate field. It is not a viewport source.
- S1-1 import consumes `ReferenceMarkerSeed.createForIncident(incidentId, seedMarkers)` to create initial reference markers from mock 112 last confirmed location and reporter statement location.
- S5 owns initial reference marker creation and `MarkerQuery.byIncident`.
- S7 offline manifest includes `initialMarkers` and `overallSearchArea`, but manifest can fail with `overall_search_area_required` when the active overall area is missing.

## UI States

### Overall Area Ready

Condition:

- Active `areaLevel=OVERALL`, `status=ACTIVE` search area exists.

Behavior:

- Fit map to `overallSearchArea.bbox` when available.
- If `bbox` is absent, compute bounds from GeoJSON `Polygon` coordinates.
- Render the overall search polygon.
- Enable UNIT/TEAM area creation, split, and assignment workflows.

### Overall Area Required

Condition:

- Overall search area query returns `409 { "error": "overall_search_area_required" }`.

Behavior:

- Load initial/reference markers.
- Fit map to marker bounds when one or more markers exist.
- If no marker exists, use jurisdiction/default map fallback.
- Show the "set overall search area" command flow.
- Keep area split/assignment and offline package readiness actions disabled until the overall area is saved.

### Load Error

Condition:

- Incident access, channel, network, or unexpected server error.

Behavior:

- Preserve incident context if loaded.
- Show retry affordance for map source queries.
- Do not silently create a search area or infer a polygon.

## Data Shapes

### Overall Search Area

```ts
type BBox = [minLon: number, minLat: number, maxLon: number, maxLat: number];

type OverallSearchArea = {
  id: string;
  incidentId: string;
  status: 'ACTIVE';
  version: number;
  geometry: {
    type: 'Polygon';
    coordinates: Array<Array<[lon: number, lat: number]>>;
  };
  bbox?: BBox;
};
```

### Marker Anchor

Use the S5 marker query shape. Coordinates are EPSG:4326 Point values in `[lon, lat]` order.

```ts
type MapAnchorMarker = {
  id: string;
  incidentId: string;
  opId: string;
  type: 'CLUE' | 'PERSON_FOUND' | 'FIELD_CONDITION' | 'SUPPORT_REQUEST' | 'NOTE';
  status: 'ACTIVE' | 'UPDATED';
  version: number;
  location: {
    type: 'Point';
    coordinates: [lon: number, lat: number];
  };
  source?: 'APP' | 'WEB' | 'MOCK_SEED' | 'SYSTEM';
  memo?: string | null;
};
```

## Fetch Plan

On situation board incident entry:

1. Fetch incident detail or board shell context.
2. Fetch active overall search area.
3. Fetch board snapshot or marker slot data.
4. Resolve initial viewport with:

```ts
if (overallSearchArea) {
  fitOverallArea(overallSearchArea);
} else if (initialMarkers.length > 0) {
  fitMarkerBounds(initialMarkers);
} else {
  fitDefaultJurisdiction();
}
```

## Implementation Notes

- Do not add coordinates to `missingPerson` unless the API contract is updated first.
- Do not create a synthetic overall polygon from marker points.
- Do not treat current map viewport as the official search area until the user saves `areaLevel=OVERALL`.
- Do not enable offline package ready/use state before active overall search area exists.
- Marker absence should be treated as degraded seed data, not as a fatal page error.

## Acceptance Checklist

- Incident detail page does not expect search area fields from `GET /api/incidents/{incidentId}`.
- Situation board opens on overall area when it exists.
- Situation board opens on initial reference marker bounds when overall area is missing.
- Situation board still opens with default fallback when both overall area and markers are missing.
- Overall area required state clearly leads to the web command for drawing/saving the overall search area.
- Area split/assignment actions are unavailable until active overall area exists.
