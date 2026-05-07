import { Navigate, Route, Routes } from 'react-router-dom';
import { SituationBoardPage } from '../features/board/pages/SituationBoardPage';

const bootstrapIncidentId = 'inc-precinct-first-001';

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to={`/incidents/${bootstrapIncidentId}/board`} replace />} />
      <Route path="/incidents/:incidentId/board" element={<SituationBoardPage />} />
      <Route path="*" element={<Navigate to={`/incidents/${bootstrapIncidentId}/board`} replace />} />
    </Routes>
  );
}
