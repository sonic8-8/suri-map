import { RadioTower } from 'lucide-react';
import { getApiBaseUrl } from '../../../../../shared/config';

export function CurrentOperationStatus() {
  return (
    <section className="current-operation-status" aria-label="현재 운용 상태">
      <div>
        <span className="eyebrow">상태</span>
        <strong>진행 중 · 현재 OP 3차 · 재수색</strong>
      </div>
      <div>
        <span className="eyebrow">API</span>
        <strong>{getApiBaseUrl()}</strong>
      </div>
      <RadioTower size={18} aria-hidden="true" />
    </section>
  );
}
