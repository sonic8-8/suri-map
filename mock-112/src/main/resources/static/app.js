// mock 112 Admin — vanilla JS
const BASE = '';

let assignableOrganizations = [];
let incidents = [];
let selectedIncidentId = null;
let incidentLoadState = 'idle';
let incidentLoadError = '';
let organizationLoadState = 'idle';
let lastMutationResult = null;
const deliveryByIncidentId = {};

function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3200);
}

async function api(method, path, body) {
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) {
        opts.body = JSON.stringify(body);
    }
    const res = await fetch(BASE + path, opts);
    const data = await res.json();
    if (!res.ok) {
        throw { status: res.status, data };
    }
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

function jsArg(value) {
    return escapeHtml(JSON.stringify(String(value ?? '')));
}

function formatDateTime(value) {
    if (!value) {
        return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return new Intl.DateTimeFormat('ko-KR', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
    }).format(date);
}

function caseLabel(incident) {
    return incident?.caseNumber || incident?.sourceIncidentId || '-';
}

function selectedIncident() {
    return incidents.find(incident => incident.sourceIncidentId === selectedIncidentId) || null;
}

function organizationByCode(code) {
    return assignableOrganizations.find(org => org.organizationCode === code);
}

function organizationOptionsHtml() {
    if (!assignableOrganizations.length) {
        return '<option value="">배정 가능 조직 없음</option>';
    }
    return assignableOrganizations
        .map(org => `<option value="${escapeHtml(org.organizationCode)}">${escapeHtml(org.organizationName)}</option>`)
        .join('');
}

function accountSummary(org) {
    if (organizationLoadState === 'error') {
        return '배정 가능 조직을 불러오지 못했습니다.';
    }
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
        const previous = initialSelect.value;
        initialSelect.innerHTML = organizationOptionsHtml();
        initialSelect.value = organizationByCode(previous)
            ? previous
            : (assignableOrganizations[0]?.organizationCode || '');
        renderInitialOrganizationPreview();
    }

    document.querySelectorAll('select[data-assignment-select="true"]').forEach(select => {
        const previous = select.value;
        select.innerHTML = organizationOptionsHtml();
        select.value = organizationByCode(previous)
            ? previous
            : (assignableOrganizations[0]?.organizationCode || '');
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
    organizationLoadState = 'loading';
    try {
        assignableOrganizations = await api('GET', '/mock-112/assignable-organizations');
        organizationLoadState = 'loaded';
    } catch (e) {
        console.error('배정 가능 조직 로드 실패', e);
        assignableOrganizations = [];
        organizationLoadState = 'error';
        showToast('배정 가능 조직을 불러오지 못했습니다.', 'error');
    }
    populateAssignmentGroupOptions();
    renderIncidentTable();
    renderIncidentDetail();
}

async function loadIncidents() {
    incidentLoadState = incidents.length ? 'refreshing' : 'loading';
    incidentLoadError = '';
    renderIncidentTable();
    updateIncidentListState();
    try {
        incidents = await api('GET', '/mock-112/incidents');
        await loadWebhookSourceStatuses(incidents.map(incident => incident.sourceIncidentId));
        incidentLoadState = 'loaded';
        if (incidents.length && !selectedIncident()) {
            selectedIncidentId = incidents[0].sourceIncidentId;
        }
        if (!incidents.length) {
            selectedIncidentId = null;
        }
        renderIncidentTable();
        renderIncidentDetail();
        updateStatusCounts(incidents);
    } catch (e) {
        console.error('사건 목록 로드 실패', e);
        incidentLoadState = 'error';
        incidentLoadError = e.data?.message || '사건 목록을 불러오지 못했습니다.';
        renderIncidentTable();
        renderIncidentDetail();
        updateIncidentListState();
    }
}

async function loadWebhookSourceStatuses(sourceIncidentIds) {
    if (!sourceIncidentIds.length) {
        return;
    }
    const query = sourceIncidentIds
        .map(id => `sourceIncidentId=${encodeURIComponent(id)}`)
        .join('&');
    try {
        const statuses = await api('GET', `/mock-112/webhook-outbox/sources?${query}`);
        Object.values(statuses).forEach(status => {
            deliveryByIncidentId[status.sourceIncidentId] = {
                ...status,
                actionLabel: 'outbox',
                recordedAt: status.updatedAt
            };
        });
    } catch (e) {
        console.error('Webhook outbox 상태 로드 실패', e);
    }
}

function updateIncidentListState() {
    const state = document.getElementById('incidentListState');
    if (!state) {
        return;
    }
    if (incidentLoadState === 'loading') {
        state.textContent = '불러오는 중';
    } else if (incidentLoadState === 'refreshing') {
        state.textContent = '갱신 중';
    } else if (incidentLoadState === 'error') {
        state.textContent = '오류';
    } else {
        state.textContent = `총 ${incidents.length}건`;
    }
}

function updateStatusCounts(source) {
    const ready = source.filter(i => i.status === 'READY').length;
    const imported = source.filter(i => i.status === 'IMPORTED').length;
    document.getElementById('statusReady').textContent = `READY ${ready}`;
    document.getElementById('statusImported').textContent = `IMPORTED ${imported}`;
    updateIncidentListState();
}

function incidentSearchText(incident) {
    const assignments = (incident.assignments || [])
        .map(assignment => `${assignment.accountCode} ${assignment.incidentRole}`)
        .join(' ');
    return [
        incident.caseNumber,
        incident.sourceIncidentId,
        incident.title,
        incident.status,
        incident.missingPerson?.displayName,
        incident.missingPerson?.appearanceText,
        incident.missingPerson?.lastSeenLocationText,
        assignments
    ].join(' ').toLowerCase();
}

function incidentDeliveryStatus(incident) {
    return deliveryByIncidentId[incident.sourceIncidentId]?.status || 'UNKNOWN';
}

function filteredIncidents() {
    const statusFilter = document.getElementById('incidentStatusFilter')?.value || 'ALL';
    const deliveryFilter = document.getElementById('incidentDeliveryFilter')?.value || 'ALL';
    const query = (document.getElementById('incidentSearchInput')?.value || '').trim().toLowerCase();
    return incidents.filter(incident => {
        if (statusFilter !== 'ALL' && incident.status !== statusFilter) {
            return false;
        }
        const deliveryStatus = incidentDeliveryStatus(incident);
        if (deliveryFilter !== 'ALL' && deliveryStatus !== deliveryFilter) {
            return false;
        }
        if (query && !incidentSearchText(incident).includes(query)) {
            return false;
        }
        return true;
    });
}

function renderIncidentTable() {
    const container = document.getElementById('incidentList');
    if (!container) {
        return;
    }
    updateIncidentListState();

    if (incidentLoadState === 'loading') {
        container.innerHTML = '<div class="state-panel">사건 목록을 불러오는 중입니다.</div>';
        return;
    }
    if (incidentLoadState === 'error' && !incidents.length) {
        container.innerHTML = `<div class="state-panel error">${escapeHtml(incidentLoadError)}</div>`;
        return;
    }
    if (!incidents.length) {
        container.innerHTML = '<div class="state-panel">등록된 사건이 없습니다.</div>';
        return;
    }

    const visible = filteredIncidents();
    if (!visible.length) {
        container.innerHTML = '<div class="state-panel">필터와 일치하는 사건이 없습니다.</div>';
        return;
    }

    container.innerHTML = `
        <div class="table-wrap">
            <table class="incident-table">
                <thead>
                    <tr>
                        <th>사건번호</th>
                        <th>상태</th>
                        <th>신고 내용</th>
                        <th>배정</th>
                        <th>마커</th>
                        <th>최근 Webhook</th>
                        <th>작업</th>
                    </tr>
                </thead>
                <tbody>
                    ${visible.map(renderIncidentRow).join('')}
                </tbody>
            </table>
        </div>`;
}

function renderIncidentRow(incident) {
    const statusClass = (incident.status || '').toLowerCase();
    const assignmentCount = (incident.assignments || []).length;
    const markerCount = (incident.seedMarkers || []).length;
    const personName = incident.missingPerson?.displayName || '실종자 미입력';
    const delivery = deliveryByIncidentId[incident.sourceIncidentId];
    const rowSelected = incident.sourceIncidentId === selectedIncidentId ? 'selected' : '';
    const selectId = `assignGroup-${incident.sourceIncidentId}`;
    return `
        <tr class="${rowSelected}">
            <td>
                <button class="case-button" onclick="selectIncident(${jsArg(incident.sourceIncidentId)})">
                    ${escapeHtml(caseLabel(incident))}
                </button>
                <span class="table-subtext">${escapeHtml(formatDateTime(incident.openedAt || incident.createdAt))}</span>
            </td>
            <td><span class="status-chip ${statusClass}">${escapeHtml(incident.status)}</span></td>
            <td>
                <strong>${escapeHtml(incident.title || '-')}</strong>
                <span class="table-subtext">${escapeHtml(personName)}</span>
            </td>
            <td>${assignmentCount}명</td>
            <td>${markerCount}개</td>
            <td>${renderDeliveryBadge(delivery)}</td>
            <td>
                <div class="row-actions">
                    <select id="${escapeHtml(selectId)}" data-assignment-select="true" aria-label="조직 배정">
                        ${organizationOptionsHtml()}
                    </select>
                    <button class="btn btn-sm btn-primary" onclick="assignGroup(${jsArg(incident.sourceIncidentId)}, ${jsArg(selectId)})">
                        배정
                    </button>
                    <button class="btn btn-sm btn-muted" onclick="selectIncident(${jsArg(incident.sourceIncidentId)})">
                        상세
                    </button>
                </div>
            </td>
        </tr>`;
}

function selectIncident(sourceIncidentId) {
    selectedIncidentId = sourceIncidentId;
    renderIncidentTable();
    renderIncidentDetail();
}

function renderIncidentDetail() {
    const panel = document.getElementById('incidentDetail');
    if (!panel) {
        return;
    }
    const incident = selectedIncident();
    if (!incident) {
        panel.innerHTML = `
            <div class="detail-empty">
                <h3>사건 상세</h3>
                <p>테이블에서 사건을 선택하세요.</p>
            </div>`;
        return;
    }

    const delivery = deliveryByIncidentId[incident.sourceIncidentId];
    const assignments = incident.assignments || [];
    const markers = incident.seedMarkers || [];
    const person = incident.missingPerson || {};
    panel.innerHTML = `
        <div class="detail-header">
            <div>
                <p class="eyebrow">선택 사건</p>
                <h3>${escapeHtml(caseLabel(incident))}</h3>
                <p class="detail-title">${escapeHtml(incident.title || '-')}</p>
            </div>
            <span class="status-chip ${(incident.status || '').toLowerCase()}">${escapeHtml(incident.status)}</span>
        </div>

        <dl class="detail-grid">
            <div><dt>원천 ID</dt><dd><code>${escapeHtml(incident.sourceIncidentId)}</code></dd></div>
            <div><dt>접수 시각</dt><dd>${escapeHtml(formatDateTime(incident.openedAt))}</dd></div>
            <div><dt>실종자</dt><dd>${escapeHtml(person.displayName || '-')}</dd></div>
            <div><dt>인상착의</dt><dd>${escapeHtml(person.appearanceText || '-')}</dd></div>
            <div class="full"><dt>마지막 목격 위치</dt><dd>${escapeHtml(person.lastSeenLocationText || '-')}</dd></div>
        </dl>

        <div class="detail-section">
            <div class="detail-section-header">
                <h4>배정 계정 ${assignments.length}명</h4>
                <button class="link-button" onclick="copyId(${jsArg(incident.sourceIncidentId)})">원천 ID 복사</button>
            </div>
            ${renderAssignmentList(assignments)}
        </div>

        <div class="detail-section">
            <h4>기준 마커 ${markers.length}개</h4>
            ${renderMarkerList(markers)}
        </div>

        <div class="detail-section">
            <h4>최근 Webhook</h4>
            ${renderDeliverySummary(delivery)}
        </div>

        <div class="detail-actions">
            <select id="detailAssignGroup" data-assignment-select="true" aria-label="상세 조직 배정">
                ${organizationOptionsHtml()}
            </select>
            <button class="btn btn-primary" onclick="assignGroup(${jsArg(incident.sourceIncidentId)}, 'detailAssignGroup')">조직 배정</button>
            <button class="btn btn-warning" onclick="handover(${jsArg(incident.sourceIncidentId)})">실종팀 인계</button>
            <button class="btn btn-success" onclick="addSupport(${jsArg(incident.sourceIncidentId)})">지원 부대</button>
        </div>`;
}

function renderAssignmentList(assignments) {
    if (!assignments.length) {
        return '<p class="muted">배정된 계정이 없습니다.</p>';
    }
    return `
        <div class="mini-table-wrap">
            <table class="mini-table">
                <thead>
                    <tr><th>계정</th><th>역할</th><th>배정 시각</th></tr>
                </thead>
                <tbody>
                    ${assignments.map(assignment => `
                        <tr>
                            <td><code>${escapeHtml(assignment.accountCode)}</code></td>
                            <td>${escapeHtml(assignment.incidentRole || '-')}</td>
                            <td>${escapeHtml(formatDateTime(assignment.assignedAt))}</td>
                        </tr>`).join('')}
                </tbody>
            </table>
        </div>`;
}

function renderMarkerList(markers) {
    if (!markers.length) {
        return '<p class="muted">등록된 기준 마커가 없습니다.</p>';
    }
    return `
        <ul class="marker-list">
            ${markers.map(marker => `
                <li>
                    <span>${escapeHtml(marker.type || '-')} · ${escapeHtml(marker.source || '-')}</span>
                    <strong>${escapeHtml(marker.memo || '-')}</strong>
                    <code>${Number(marker.lat).toFixed(5)}, ${Number(marker.lon).toFixed(5)}</code>
                </li>`).join('')}
        </ul>`;
}

function renderDeliveryBadge(delivery) {
    if (!delivery) {
        return '<span class="delivery-badge unknown">기록 없음</span>';
    }
    const status = delivery.status || 'UNKNOWN';
    return `<span class="delivery-badge ${escapeHtml(status.toLowerCase())}">${escapeHtml(status)}</span>`;
}

function renderDeliverySummary(delivery) {
    if (!delivery) {
        return '<p class="muted">현재 브라우저 세션에서 실행한 전송 기록이 없습니다.</p>';
    }
    return `
        <div class="delivery-summary">
            ${renderDeliveryBadge(delivery)}
            <span>${escapeHtml(delivery.actionLabel || '작업')}</span>
            <span>events ${escapeHtml(delivery.events ?? 0)}</span>
            <span>sent ${escapeHtml(delivery.sent ?? 0)}</span>
            <span>pending ${escapeHtml(delivery.pending ?? 0)}</span>
            <span>failed ${escapeHtml(delivery.failed ?? 0)}</span>
            ${delivery.attemptCount != null ? `<span>attempts ${escapeHtml(delivery.attemptCount)}</span>` : ''}
            <span>${escapeHtml(formatDateTime(delivery.recordedAt))}</span>
        </div>`;
}

function rememberDelivery(sourceIncidentId, delivery, actionLabel) {
    if (!sourceIncidentId || !delivery) {
        return;
    }
    deliveryByIncidentId[sourceIncidentId] = {
        ...delivery,
        actionLabel,
        recordedAt: new Date().toISOString()
    };
}

function renderMutationResult(result) {
    const panel = document.getElementById('createResult');
    if (!panel) {
        return;
    }
    if (!result) {
        panel.innerHTML = '';
        panel.classList.add('hidden');
        return;
    }
    panel.classList.remove('hidden');
    panel.innerHTML = `
        <div class="result-header">
            <strong>${escapeHtml(result.title)}</strong>
            ${renderDeliveryBadge(result.webhookDelivery)}
        </div>
        <div class="result-grid">
            <span>사건번호 <b>${escapeHtml(result.caseNumber || '-')}</b></span>
            <span>배정 ${escapeHtml(result.assignmentCount ?? result.addedCount ?? 0)}건</span>
            ${result.seedMarkerCount != null ? `<span>마커 ${escapeHtml(result.seedMarkerCount)}개</span>` : ''}
        </div>
        <button type="button" class="link-button" onclick="copyId(${jsArg(result.sourceIncidentId)})">
            원천 ID 복사
        </button>`;
}

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
        rememberDelivery(created.sourceIncidentId, created.webhookDelivery, '사건 등록');
        lastMutationResult = { ...created, title: '사건 등록 완료' };
        renderMutationResult(lastMutationResult);
        showToast(`사건 등록 완료: ${created.caseNumber}`, 'success');
        document.getElementById('incidentForm').reset();
        populateAssignmentGroupOptions();
        selectedIncidentId = created.sourceIncidentId;
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast(`등록 실패: ${e.data?.message || '알 수 없는 오류'}`, 'error');
    }
}

async function seedPrecinctFirst() {
    try {
        const res = await api('POST', '/mock-112/scenarios/precinct-first');
        rememberDelivery(res.sourceIncidentId, res.webhookDelivery, '시나리오 Seed');
        lastMutationResult = { ...res, title: '시나리오 Seed 완료' };
        renderMutationResult(lastMutationResult);
        selectedIncidentId = res.sourceIncidentId;
        showToast(`시나리오 적재 완료: ${res.caseNumber || res.sourceIncidentId}`, 'success');
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast(`Seed 실패: ${e.data?.message || '이미 적재됨'}`, 'error');
    }
}

async function handover(sourceIncidentId) {
    try {
        const result = await api('POST', `/mock-112/incidents/${sourceIncidentId}/handover-to-missing-team`);
        rememberDelivery(sourceIncidentId, result.webhookDelivery, '실종팀 인계');
        showToast(`실종팀 인계 완료: 신규 ${result.addedCount}건`, 'success');
        selectedIncidentId = sourceIncidentId;
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast(`인계 실패: ${e.data?.message || ''}`, 'error');
    }
}

async function addSupport(sourceIncidentId) {
    try {
        const result = await api('POST', `/mock-112/incidents/${sourceIncidentId}/add-support-unit`);
        rememberDelivery(sourceIncidentId, result.webhookDelivery, '지원 부대 배정');
        showToast(`지원 부대 배정 완료: 신규 ${result.addedCount}건`, 'success');
        selectedIncidentId = sourceIncidentId;
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast(`배정 실패: ${e.data?.message || ''}`, 'error');
    }
}

async function assignGroup(sourceIncidentId, selectId) {
    const select = document.getElementById(selectId || `assignGroup-${sourceIncidentId}`);
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
        rememberDelivery(sourceIncidentId, result.webhookDelivery, `${org.organizationName} 배정`);
        const skipped = org.accounts.length - result.addedCount;
        const skippedText = skipped > 0 ? `, 기존 배정 ${skipped}건 유지` : '';
        showToast(`${org.organizationName} 배정 완료: 신규 ${result.addedCount}건${skippedText}`, 'success');
        selectedIncidentId = sourceIncidentId;
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast(`조직 배정 실패: ${e.data?.message || '알 수 없는 오류'}`, 'error');
    }
}

async function resetAll() {
    const answer = prompt('mock-112 사건, 배정, webhook outbox를 모두 삭제합니다. RESET을 입력하면 진행합니다.');
    if (answer !== 'RESET') {
        showToast('초기화를 취소했습니다.', 'info');
        return;
    }
    try {
        await api('POST', '/mock-112/reset');
        incidents = [];
        selectedIncidentId = null;
        Object.keys(deliveryByIncidentId).forEach(key => delete deliveryByIncidentId[key]);
        lastMutationResult = null;
        renderMutationResult(null);
        showToast('초기화 완료', 'info');
        await loadIncidents();
        await loadHealth();
    } catch (e) {
        showToast('초기화 실패', 'error');
    }
}

function copyId(id) {
    navigator.clipboard.writeText(id).then(() => {
        showToast(`복사됨: ${id}`, 'info');
    });
}

async function loadHealth() {
    try {
        const h = await api('GET', '/mock-112/health');
        const outbox = h.webhookOutbox || {};
        document.getElementById('healthInfo').innerHTML = `
            <div class="health-row"><span>전체 사건</span><strong>${escapeHtml(h.incidentCount)}</strong></div>
            <div class="health-row"><span>READY</span><strong>${escapeHtml(h.readyCount)}</strong></div>
            <div class="health-row"><span>IMPORTED</span><strong>${escapeHtml(h.importedCount)}</strong></div>
            <div class="health-row"><span>Webhook PENDING</span><strong>${escapeHtml(outbox.PENDING ?? 0)}</strong></div>
            <div class="health-row"><span>Webhook FAILED</span><strong>${escapeHtml(outbox.FAILED ?? 0)}</strong></div>
        `;
        const serverStatus = document.getElementById('serverStatus');
        serverStatus.textContent = '연결됨';
        serverStatus.className = 'status-pill server ok';
    } catch (e) {
        const serverStatus = document.getElementById('serverStatus');
        serverStatus.textContent = '연결 실패';
        serverStatus.className = 'status-pill server error';
        document.getElementById('healthInfo').innerHTML = '<div class="state-panel error">서버 상태를 확인하지 못했습니다.</div>';
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    renderMutationResult(lastMutationResult);
    ['incidentStatusFilter', 'incidentDeliveryFilter', 'incidentSearchInput'].forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.addEventListener('input', () => {
                renderIncidentTable();
                renderIncidentDetail();
            });
            element.addEventListener('change', () => {
                renderIncidentTable();
                renderIncidentDetail();
            });
        }
    });
    const initialSelect = document.getElementById('inputInitialAssignmentGroup');
    if (initialSelect) {
        initialSelect.addEventListener('change', renderInitialOrganizationPreview);
    }
    await loadAssignableOrganizations();
    await loadIncidents();
    await loadHealth();
    setInterval(() => {
        loadIncidents();
        loadHealth();
    }, 5000);
});
