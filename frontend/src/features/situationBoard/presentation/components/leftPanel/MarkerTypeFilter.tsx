import { Binoculars, CircleHelp, Hand, MapPinned, NotebookPen, PawPrint, Plane, Search } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { markerTypes, supportMarkerTypes } from '../../constants/mockSituationBoard';

const markerIconMap: Record<string, LucideIcon> = {
  clue: Search,
  dog: PawPrint,
  drone: Plane,
  field: MapPinned,
  found: Binoculars,
  note: NotebookPen,
  support: Hand,
};

export function MarkerTypeFilter() {
  return (
    <section className="left-panel-section" aria-labelledby="marker-type-title">
      <h2 id="marker-type-title">마커 유형</h2>
      <div className="marker-chip-list">
        {markerTypes.map(({ label, icon }) => {
          const Icon = markerIconMap[icon];

          return (
            <button key={label} type="button" className="marker-type-chip">
              <Icon size={15} aria-hidden="true" />
              <span>{label}</span>
            </button>
          );
        })}
        <button type="button" className="marker-type-chip marker-type-chip-wide">
          <CircleHelp size={15} aria-hidden="true" />
          <span>지원 요청 (3종)</span>
        </button>
      </div>
      <div className="marker-support-list">
        {supportMarkerTypes.map(({ label, icon }) => {
          const Icon = markerIconMap[icon];

          return (
            <button key={label} type="button" className="marker-support-chip">
              <Icon size={14} aria-hidden="true" />
              <span>{label}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
