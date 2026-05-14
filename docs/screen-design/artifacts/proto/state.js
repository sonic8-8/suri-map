// Suri-Map proto state — localStorage 기반 시연용 mock state
const STATE_KEY = 'suri-map-proto-state';

const DEFAULT_STATE = {
  step: 0,
  loggedIn: false,
  account: null,
  incidents: [
    // mock 112 candidates (가져오기 전)
    { id: '#2001', missing: '정OO 70대 남', location: '광주 남구', assignedAt: '13:50', imported: false },
    { id: '#2002', missing: '최OO 50대 여', location: '광주 북구', assignedAt: '14:10', imported: false },
  ],
  importedIncidents: [],
  activeIncidentId: null,
  areasDivided: false,
  packageDownloaded: false,
  searchStarted: false,
  markers: [],
  closed: false,
};

function loadState() {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (raw) return { ...DEFAULT_STATE, ...JSON.parse(raw) };
  } catch (e) {}
  return { ...DEFAULT_STATE };
}

function saveState(state) {
  localStorage.setItem(STATE_KEY, JSON.stringify(state));
}

function getState() {
  return loadState();
}

function updateState(patch) {
  const s = loadState();
  const next = { ...s, ...patch };
  saveState(next);
  return next;
}

function resetState() {
  localStorage.removeItem(STATE_KEY);
  saveState({ ...DEFAULT_STATE });
}

// 시연 step 진행 도우미
function goToNext(href) {
  setTimeout(() => location.href = href, 300);
}

// 공통 시연용 toolbar (각 화면 우상단에 표시)
function renderProtoBar(currentStep, totalSteps, nextHref) {
  const s = getState();
  const bar = document.createElement('div');
  bar.style.cssText = `
    position: fixed; top: 12px; right: 12px; z-index: 9999;
    background: rgba(11, 15, 25, 0.95); color: #fff;
    border: 1px solid #3B4453; border-radius: 8px;
    padding: 8px 12px; display: flex; gap: 12px; align-items: center;
    font-family: Pretendard, "Noto Sans KR", sans-serif; font-size: 11px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  `;
  bar.innerHTML = `
    <span>시연 Step <b>${currentStep}</b> / ${totalSteps}</span>
    <a href="../index.html" style="color:#22D3EE; text-decoration:none; font-size:11px;">↩ 진입점</a>
    ${nextHref ? `<a href="${nextHref}" style="color:#22C55E; text-decoration:none; font-weight:bold;">다음 →</a>` : ''}
    <button onclick="if(confirm('상태 초기화?')){localStorage.clear();location.href='../index.html'}" style="background:transparent; border:1px solid #6B7280; color:#9CA3AF; font-size:10px; padding:2px 6px; border-radius:3px; cursor:pointer; font-family:inherit;">리셋</button>
  `;
  document.body.appendChild(bar);
}
