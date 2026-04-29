import { Activity, AlertTriangle, Layers, MapPin, RadioTower, Route, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import { DashboardMapShell } from './features/map/DashboardMapShell';
import { getApiBaseUrl } from './shared/config';

const activeIncident = {
  code: 'MOCK-112-001',
  title: '반포 한강공원 일대',
  operationPeriod: 'OP 1차',
  teamsOnline: 4,
  pendingSyncCount: 7,
};

export function App() {
  return (
    <main className="app-shell">
      <aside className="sidebar" aria-label="사건 목록">
        <div className="brand">
          <ShieldCheck size={24} aria-hidden="true" />
          <span>Suri-Map</span>
        </div>

        <section className="incident-card" aria-label="현재 사건">
          <span className="eyebrow">진행 중</span>
          <h1>{activeIncident.title}</h1>
          <p>{activeIncident.code}</p>
          <div className="status-grid">
            <StatusItem icon={<RadioTower size={18} />} label="온라인 단말" value={`${activeIncident.teamsOnline}대`} />
            <StatusItem icon={<Activity size={18} />} label="현재 차수" value={activeIncident.operationPeriod} />
            <StatusItem icon={<AlertTriangle size={18} />} label="미전송 큐" value={`${activeIncident.pendingSyncCount}건`} />
          </div>
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
            <button type="button" className="primary-button">새로고침</button>
          </div>
        </header>

        <DashboardMapShell />
      </section>
    </main>
  );
}

function StatusItem({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="status-item">
      <span className="status-icon">{icon}</span>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
