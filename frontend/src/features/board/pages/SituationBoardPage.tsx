import { Layers, MapPin, RadioTower, Route, ShieldCheck } from 'lucide-react';
import { useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { getApiBaseUrl } from '../../../shared/config';
import { DashboardMapShell } from '../components/DashboardMapShell';
import { useBoardDisplayStore } from '../model/boardDisplayStore';

export function SituationBoardPage() {
  const { incidentId = 'inc-precinct-first-001' } = useParams();
  const setIncidentId = useBoardDisplayStore((state) => state.setIncidentId);

  useEffect(() => {
    setIncidentId(incidentId);
  }, [incidentId, setIncidentId]);

  return (
    <main className="app-shell">
      <aside className="sidebar" aria-label="사건 목록">
        <div className="brand">
          <ShieldCheck size={24} aria-hidden="true" />
          <span>Suri-Map</span>
        </div>

        <section className="incident-card" aria-label="현재 사건">
          <span className="eyebrow">Incident</span>
          <h1>{incidentId}</h1>
        </section>

        <nav className="tool-list" aria-label="상황판 메뉴">
          <button type="button" className="tool-button active" title="지도">
            <MapPin size={18} aria-hidden="true" />
            <span>지도</span>
          </button>
          <button type="button" className="tool-button" title="레이어">
            <Layers size={18} aria-hidden="true" />
            <span>레이어</span>
          </button>
          <button type="button" className="tool-button" title="경로">
            <Route size={18} aria-hidden="true" />
            <span>경로</span>
          </button>
        </nav>
      </aside>

      <section className="workspace" aria-label="상황판">
        <header className="topbar">
          <div>
            <p className="eyebrow">API</p>
            <strong>{getApiBaseUrl()}</strong>
          </div>
          <div className="topbar-actions">
            <button type="button" className="icon-button" title="실시간 상태">
              <RadioTower size={18} aria-hidden="true" />
            </button>
            <button type="button" className="primary-button">
              새로고침
            </button>
          </div>
        </header>

        <DashboardMapShell />
      </section>
    </main>
  );
}
