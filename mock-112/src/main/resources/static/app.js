// mock 112 Admin — vanilla JS
const BASE = '';  // same-origin (서버가 static을 서빙)

// ── 유틸 ──
function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

async function api(method, path, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(BASE + path, opts);
    const data = await res.json();
    if (!res.ok) throw { status: res.status, data };
    return data;
}

// ── 사건 목록 로드 ──
async function loadIncidents() {
    try {
        const incidents = await api('GET', '/mock-112/incidents');
        renderIncidents(incidents);
        updateStatusCounts(incidents);
    } catch (e) {
        console.error('사건 목록 로드 실패', e);
    }
}

function renderIncidents(incidents) {
    const container = document.getElementById('incidentList');
    if (!incidents.length) {
        container.innerHTML = '<p class="empty-state">등록된 사건이 없습니다. 시나리오 Seed를 실행하세요.</p>';
        return;
    }
    container.innerHTML = incidents.map(inc => {
        const statusClass = inc.status.toLowerCase();
        const personInfo = inc.missingPerson
            ? `${inc.missingPerson.displayName} · ${inc.missingPerson.appearanceText || ''}`
            : '실종자 정보 없음';
        const assignCount = (inc.assignments || []).length;
        const markerCount = (inc.seedMarkers || []).length;
        return `
        <div class="incident-item ${statusClass}">
            <div class="incident-header">
                <span class="incident-id" onclick="copyId('${inc.sourceIncidentId}')" title="클릭하여 복사">
                    📋 ${inc.sourceIncidentId}
                </span>
                <span class="incident-status ${statusClass}">${inc.status}</span>
            </div>
            <div class="incident-title">${inc.title}</div>
            <div class="incident-meta">
                👤 ${personInfo} · 배정 ${assignCount}명 · 마커 ${markerCount}개
            </div>
            <div class="incident-actions">
                <button class="btn btn-sm btn-warning" onclick="handover('${inc.sourceIncidentId}')"
                    ${inc.status === 'IMPORTED' ? 'disabled' : ''}>
                    🔄 실종팀 인계
                </button>
                <button class="btn btn-sm btn-success" onclick="addSupport('${inc.sourceIncidentId}')"
                    ${inc.status === 'IMPORTED' ? 'disabled' : ''}>
                    🚓 지원 부대 배정
                </button>
                <button class="btn btn-sm btn-primary" onclick="copyId('${inc.sourceIncidentId}')">
                    📋 ID 복사
                </button>
            </div>
        </div>`;
    }).join('');
}

function updateStatusCounts(incidents) {
    const ready = incidents.filter(i => i.status === 'READY').length;
    const imported = incidents.filter(i => i.status === 'IMPORTED').length;
    document.getElementById('statusReady').textContent = `READY: ${ready}`;
    document.getElementById('statusImported').textContent = `IMPORTED: ${imported}`;
}

// ── 사건 등록 ──
async function createIncident(e) {
    e.preventDefault();
    const sourceIncidentId = document.getElementById('inputSourceId').value.trim();
    const title = document.getElementById('inputTitle').value.trim();
    const personName = document.getElementById('inputPersonName').value.trim();
    const appearance = document.getElementById('inputAppearance').value.trim();
    const lastSeen = document.getElementById('inputLastSeen').value.trim();

    const body = {
        sourceIncidentId, title,
        openedAt: new Date().toISOString(),
        missingPerson: {
            displayName: personName || '미입력',
            appearanceText: appearance || '',
            lastSeenLocationText: lastSeen || ''
        },
        assignments: [],
        seedMarkers: []
    };

    try {
        await api('POST', '/mock-112/incidents', body);
        showToast(`사건 등록 완료: ${sourceIncidentId}`, 'success');
        document.getElementById('incidentForm').reset();
        await loadIncidents();
    } catch (e) {
        showToast(`등록 실패: ${e.data?.message || '알 수 없는 오류'}`, 'error');
    }
}

// ── 시나리오 Seed ──
async function seedPrecinctFirst() {
    try {
        const res = await api('POST', '/mock-112/scenarios/precinct-first');
        showToast(`시나리오 적재 완료: ${res.sourceIncidentId}`, 'success');
        await loadIncidents();
    } catch (e) {
        showToast(`Seed 실패: ${e.data?.message || '이미 적재됨'}`, 'error');
    }
}

// ── 인계·지원 ──
async function handover(sourceIncidentId) {
    try {
        await api('POST', `/mock-112/incidents/${sourceIncidentId}/handover-to-missing-team`);
        showToast('실종팀 인계 완료', 'success');
        await loadIncidents();
    } catch (e) {
        showToast(`인계 실패: ${e.data?.message || ''}`, 'error');
    }
}

async function addSupport(sourceIncidentId) {
    try {
        await api('POST', `/mock-112/incidents/${sourceIncidentId}/add-support-unit`);
        showToast('지원 부대 배정 완료', 'success');
        await loadIncidents();
    } catch (e) {
        showToast(`배정 실패: ${e.data?.message || ''}`, 'error');
    }
}

// ── 초기화 ──
async function resetAll() {
    if (!confirm('모든 mock 112 데이터를 초기화합니다. 계속하시겠습니까?')) return;
    try {
        await api('POST', '/mock-112/reset');
        showToast('초기화 완료', 'info');
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast('초기화 실패', 'error');
    }
}

// ── ID 복사 ──
function copyId(id) {
    navigator.clipboard.writeText(id).then(() => {
        showToast(`복사됨: ${id}`, 'info');
    });
}

// ── 헬스 체크 ──
async function loadHealth() {
    try {
        const h = await api('GET', '/mock-112/health');
        document.getElementById('healthInfo').innerHTML = `
            <p>📊 전체: <strong>${h.incidentCount}</strong>건</p>
            <p>🔵 READY: <strong>${h.readyCount}</strong>건</p>
            <p>🟢 IMPORTED: <strong>${h.importedCount}</strong>건</p>
        `;
        document.getElementById('serverStatus').textContent = '● 연결됨';
        document.getElementById('serverStatus').style.color = '#06d6a0';
    } catch (e) {
        document.getElementById('serverStatus').textContent = '● 연결 실패';
        document.getElementById('serverStatus').style.color = '#ef476f';
    }
}

// ── 초기화·자동 갱신 ──
document.addEventListener('DOMContentLoaded', () => {
    loadIncidents();
    loadHealth();
    // 5초마다 자동 갱신
    setInterval(() => {
        loadIncidents();
        loadHealth();
    }, 5000);
});
