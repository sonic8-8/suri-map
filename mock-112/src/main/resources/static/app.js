// mock 112 Admin — vanilla JS
const BASE = '';  // same-origin (서버가 static을 서빙)

let assignableOrganizations = [];

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

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function organizationByCode(code) {
    return assignableOrganizations.find(org => org.organizationCode === code);
}

function organizationOptionsHtml() {
    return assignableOrganizations
        .map(org => `<option value="${escapeHtml(org.organizationCode)}">${escapeHtml(org.organizationName)}</option>`)
        .join('');
}

function accountSummary(org) {
    if (!org || !org.accounts?.length) {
        return '배정 가능한 계정 없음';
    }
    return org.accounts
        .map(account => `${account.displayName} (${account.incidentRole})`)
        .join(', ');
}

function populateAssignmentGroupOptions() {
    const initialSelect = document.getElementById('inputInitialAssignmentGroup');
    if (initialSelect) {
        initialSelect.innerHTML = organizationOptionsHtml();
        initialSelect.value = assignableOrganizations[0]?.organizationCode || '';
        renderInitialOrganizationPreview();
    }

    document.querySelectorAll('select[data-assignment-select="true"]').forEach(select => {
        const previous = select.value;
        select.innerHTML = organizationOptionsHtml();
        select.value = organizationByCode(previous) ? previous : (assignableOrganizations[0]?.organizationCode || '');
    });
}

function renderInitialOrganizationPreview() {
    const preview = document.getElementById('initialAssignmentPreview');
    const select = document.getElementById('inputInitialAssignmentGroup');
    if (!preview || !select) {
        return;
    }
    preview.textContent = accountSummary(organizationByCode(select.value));
}

async function loadAssignableOrganizations() {
    try {
        assignableOrganizations = await api('GET', '/mock-112/assignable-organizations');
        populateAssignmentGroupOptions();
    } catch (e) {
        console.error('배정 가능 조직 로드 실패', e);
        assignableOrganizations = [];
        showToast('배정 가능 조직을 불러오지 못했습니다.', 'error');
    }
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
        container.innerHTML = '<p class="empty-state">등록된 사건이 없습니다. 사건을 등록하거나 시나리오 Seed를 실행하세요.</p>';
        return;
    }
    container.innerHTML = incidents.map(inc => {
        const statusClass = inc.status.toLowerCase();
        const personInfo = inc.missingPerson
            ? `${escapeHtml(inc.missingPerson.displayName)} · ${escapeHtml(inc.missingPerson.appearanceText || '')}`
            : '실종자 정보 없음';
        const assignCount = (inc.assignments || []).length;
        const markerCount = (inc.seedMarkers || []).length;
        const selectId = `assignGroup-${inc.sourceIncidentId}`;
        const groupOptions = organizationOptionsHtml();
        return `
        <div class="incident-item ${statusClass}">
            <div class="incident-header">
                <button class="incident-id" onclick="copyId('${escapeHtml(inc.sourceIncidentId)}')" title="원천 ID 복사">
                    ${escapeHtml(inc.caseNumber || inc.sourceIncidentId)}
                </button>
                <span class="incident-status ${statusClass}">${escapeHtml(inc.status)}</span>
            </div>
            <div class="incident-title">${escapeHtml(inc.title)}</div>
            <div class="incident-meta">
                원천 ID ${escapeHtml(inc.sourceIncidentId)} · ${personInfo} · 배정 ${assignCount}명 · 마커 ${markerCount}개
            </div>
            <div class="assignment-control">
                <select id="${selectId}" data-assignment-select="true" aria-label="배정 대상 조직">
                    ${groupOptions}
                </select>
                <button class="btn btn-sm btn-primary" onclick="assignGroup('${escapeHtml(inc.sourceIncidentId)}')">
                    조직 배정
                </button>
            </div>
            <div class="incident-actions">
                <button class="btn btn-sm btn-warning" onclick="handover('${escapeHtml(inc.sourceIncidentId)}')">
                    실종팀 인계
                </button>
                <button class="btn btn-sm btn-success" onclick="addSupport('${escapeHtml(inc.sourceIncidentId)}')">
                    지원 부대 배정
                </button>
                <button class="btn btn-sm btn-primary" onclick="copyId('${escapeHtml(inc.sourceIncidentId)}')">
                    ID 복사
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
    const title = document.getElementById('inputTitle').value.trim();
    const personName = document.getElementById('inputPersonName').value.trim();
    const appearance = document.getElementById('inputAppearance').value.trim();
    const lastSeen = document.getElementById('inputLastSeen').value.trim();
    const initialOrganizationCode = document.getElementById('inputInitialAssignmentGroup').value;
    if (!initialOrganizationCode) {
        showToast('초기 배정 조직을 선택하세요.', 'error');
        return;
    }

    const body = {
        title,
        openedAt: new Date().toISOString(),
        initialOrganizationCode,
        missingPerson: {
            displayName: personName || '미입력',
            appearanceText: appearance || '',
            lastSeenLocationText: lastSeen || ''
        },
        seedMarkers: []
    };

    try {
        const created = await api('POST', '/mock-112/incidents', body);
        showToast(`사건 등록 완료: ${created.caseNumber}`, 'success');
        document.getElementById('incidentForm').reset();
        populateAssignmentGroupOptions();
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

async function assignGroup(sourceIncidentId) {
    const select = document.getElementById(`assignGroup-${sourceIncidentId}`);
    const org = organizationByCode(select?.value);
    if (!org) {
        showToast('배정 대상 조직을 선택하세요.', 'error');
        return;
    }
    try {
        const result = await api(
            'POST',
            `/mock-112/incidents/${sourceIncidentId}/assignment-organizations`,
            {
                organizationCode: org.organizationCode,
                assignedAt: new Date().toISOString()
            }
        );
        const skipped = org.accounts.length - result.addedCount;
        const skippedText = skipped > 0 ? `, 기존 배정 ${skipped}건 유지` : '';
        showToast(`${org.organizationName} 배정 완료: 신규 ${result.addedCount}건${skippedText}`, 'success');
        await loadIncidents();
    } catch (e) {
        showToast(`조직 배정 실패: ${e.data?.message || '알 수 없는 오류'}`, 'error');
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
            <p>전체: <strong>${h.incidentCount}</strong>건</p>
            <p>READY: <strong>${h.readyCount}</strong>건</p>
            <p>IMPORTED: <strong>${h.importedCount}</strong>건</p>
        `;
        document.getElementById('serverStatus').textContent = '● 연결됨';
        document.getElementById('serverStatus').style.color = '#06d6a0';
    } catch (e) {
        document.getElementById('serverStatus').textContent = '● 연결 실패';
        document.getElementById('serverStatus').style.color = '#ef476f';
    }
}

// ── 초기화·자동 갱신 ──
document.addEventListener('DOMContentLoaded', async () => {
    await loadAssignableOrganizations();
    const initialSelect = document.getElementById('inputInitialAssignmentGroup');
    if (initialSelect) {
        initialSelect.addEventListener('change', renderInitialOrganizationPreview);
    }
    loadIncidents();
    loadHealth();
    // 5초마다 자동 갱신
    setInterval(() => {
        loadIncidents();
        loadHealth();
    }, 5000);
});
