// =========================================================================
// Suri-Map — vector base map
// 도심 외곽 / 논밭 — 시흥 매화동 일대를 모티브로 한 가상 지도.
// SVG, viewBox 0 0 1000 650. Includes:
//   - hill shading (west)
//   - river / 매화천 (south band)
//   - farmland parcels (rice + greenhouse cluster)
//   - village + small roads
//   - main roads + intersections
//   - rail line + bridge
//   - place labels (subtle)
// Colors target Naver-Map-ish daylight palette but slightly muted.
// Exposes: <SuriMap.Base />, <SuriMap.Defs />, color tokens.
// =========================================================================

(function () {
  // ─── Map color palette (matches a clean Korean map app) ───────────────
  const C = {
    bg:       '#E9EFE6',          // overall land base
    grass:    '#DCE8CE',          // open grass / shoulder
    forest:   '#B7CFA0',          // wooded / tree clusters
    forestDk: '#9CBE8A',
    paddy1:   '#E6EDC9',          // rice paddy A
    paddy2:   '#DCE6BC',          // rice paddy B
    paddyEdge:'#B8C68F',
    greenhouse:'#E2E8EE',
    greenhouseEdge:'#B8C2CC',
    soil:     '#D8C7A4',          // bare farmland / soil
    river:    '#B7D4DD',          // 매화천 main
    riverDk:  '#9CC3CF',
    riverBank:'#D8E6E2',
    village:  '#F2EBDD',          // village block fill
    villageEdge:'#C9BFA5',
    bldg:     '#C9C2AE',
    bldgRoof: '#A89E83',
    roadMain: '#F6E89A',          // major road (yellow-ish)
    roadMainEdge: '#D9C66E',
    roadSub:  '#FFFFFF',          // collector road
    roadSubEdge: '#C8C8C8',
    roadDirt: '#E6D9B3',          // dirt farm road
    roadDirtEdge: '#C2B58B',
    rail:     '#7A7E80',
    railTie:  '#3F4243',
    hill:     '#C9D6B6',
    hillEdge: '#A6B796',
    label:    '#5A6258',
    labelDim: '#8A9189',
  };

  function MapDefs() {
    return (
      <defs>
        <pattern id="paddyHatch" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(0)">
          <rect width="14" height="14" fill={C.paddy1} />
          <line x1="0" y1="3.5" x2="14" y2="3.5" stroke={C.paddyEdge} strokeWidth="0.4" />
          <line x1="0" y1="7"   x2="14" y2="7"   stroke={C.paddyEdge} strokeWidth="0.4" />
          <line x1="0" y1="10.5" x2="14" y2="10.5" stroke={C.paddyEdge} strokeWidth="0.4" />
        </pattern>
        <pattern id="paddyHatch2" width="14" height="14" patternUnits="userSpaceOnUse">
          <rect width="14" height="14" fill={C.paddy2} />
          <line x1="3.5" y1="0" x2="3.5" y2="14" stroke={C.paddyEdge} strokeWidth="0.4" />
          <line x1="7"   y1="0" x2="7"   y2="14" stroke={C.paddyEdge} strokeWidth="0.4" />
          <line x1="10.5" y1="0" x2="10.5" y2="14" stroke={C.paddyEdge} strokeWidth="0.4" />
        </pattern>
        <pattern id="greenhouse" width="22" height="14" patternUnits="userSpaceOnUse">
          <rect width="22" height="14" fill={C.greenhouse} />
          <path d="M0 11 Q5 4 11 11 M11 11 Q16 4 22 11" fill="none" stroke={C.greenhouseEdge} strokeWidth="0.7" />
          <line x1="0" y1="11.5" x2="22" y2="11.5" stroke={C.greenhouseEdge} strokeWidth="0.5" />
        </pattern>
        <pattern id="forestDots" width="14" height="14" patternUnits="userSpaceOnUse">
          <rect width="14" height="14" fill={C.forest} />
          <circle cx="3" cy="3" r="2" fill={C.forestDk} opacity="0.55" />
          <circle cx="10" cy="9" r="2.4" fill={C.forestDk} opacity="0.5" />
          <circle cx="6" cy="11" r="1.6" fill={C.forestDk} opacity="0.45" />
        </pattern>
        <linearGradient id="riverGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={C.riverDk} />
          <stop offset="100%" stopColor={C.river} />
        </linearGradient>
        <pattern id="hillContour" width="40" height="40" patternUnits="userSpaceOnUse">
          <rect width="40" height="40" fill={C.hill} />
          <path d="M0 30 Q20 18 40 30" fill="none" stroke={C.hillEdge} strokeWidth="0.6" opacity="0.7" />
          <path d="M0 18 Q20 6 40 18"  fill="none" stroke={C.hillEdge} strokeWidth="0.6" opacity="0.5" />
        </pattern>
        {/* glow filter for highlighted operator path (FR-25) */}
        <filter id="pathGlow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="3" result="blur"/>
          <feMerge>
            <feMergeNode in="blur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>
    );
  }

  // ─── Static base ─────────────────────────────────────────────────────
  function MapBase({ simple = false }) {
    // simple = true => FR-26 "단순 지도 보기 모드" — strip patterns, use flat fills
    const fillPaddy1 = simple ? C.paddy1 : 'url(#paddyHatch)';
    const fillPaddy2 = simple ? C.paddy2 : 'url(#paddyHatch2)';
    const fillGreen  = simple ? C.greenhouse : 'url(#greenhouse)';
    const fillForest = simple ? C.forest : 'url(#forestDots)';
    const fillHill   = simple ? C.hill : 'url(#hillContour)';

    return (
      <g>
        {/* base land */}
        <rect x="0" y="0" width="1000" height="650" fill={C.bg}/>

        {/* west hills */}
        <path d="M0,0 L0,560 L130,540 L160,460 L130,400 L100,360 L150,300 L120,240 L90,180 L130,110 L100,40 L60,0 Z" fill={fillHill}/>
        <path d="M0,560 L0,650 L200,640 L240,560 L180,500 L100,520 L40,540 Z" fill={fillForest}/>

        {/* east hills (small) */}
        <path d="M880,90 L1000,80 L1000,260 L920,250 L880,200 Z" fill={fillForest}/>
        <path d="M900,520 L1000,510 L1000,650 L880,640 L880,560 Z" fill={fillForest}/>

        {/* river / 매화천 */}
        <path d="M0,520 C140,500 280,540 360,510 C470,470 580,560 700,520 C820,490 920,560 1000,540 L1000,610 C920,620 820,580 700,610 C580,640 470,580 360,610 C280,640 140,610 0,620 Z"
              fill="url(#riverGrad)"/>
        {/* river banks (subtle highlight) */}
        <path d="M0,520 C140,500 280,540 360,510 C470,470 580,560 700,520 C820,490 920,560 1000,540"
              fill="none" stroke={C.riverBank} strokeWidth="2" opacity="0.7"/>

        {/* farmland parcels — rice paddies */}
        <g stroke={C.paddyEdge} strokeWidth="0.8" fill={fillPaddy1}>
          <rect x="200" y="180" width="120" height="80"/>
          <rect x="320" y="180" width="100" height="80"/>
          <rect x="200" y="260" width="120" height="60"/>
          <rect x="320" y="260" width="100" height="60"/>
        </g>
        <g stroke={C.paddyEdge} strokeWidth="0.8" fill={fillPaddy2}>
          <rect x="450" y="200" width="130" height="70"/>
          <rect x="580" y="200" width="120" height="70"/>
          <rect x="450" y="270" width="130" height="80"/>
          <rect x="580" y="270" width="120" height="80"/>
        </g>
        {/* southern farmland strip (between road and river bank) */}
        <g stroke={C.paddyEdge} strokeWidth="0.8" fill={fillPaddy1}>
          <rect x="280" y="380" width="130" height="50"/>
          <rect x="410" y="380" width="130" height="50"/>
          <rect x="540" y="380" width="120" height="50"/>
          <rect x="660" y="380" width="120" height="50"/>
        </g>
        <g stroke={C.paddyEdge} strokeWidth="0.8" fill={fillPaddy2}>
          <rect x="280" y="430" width="130" height="55"/>
          <rect x="410" y="430" width="130" height="55"/>
          <rect x="540" y="430" width="120" height="55"/>
          <rect x="660" y="430" width="120" height="55"/>
        </g>

        {/* bare soil patch */}
        <path d="M150,360 L260,365 L255,420 L145,420 Z" fill={C.soil} stroke={C.paddyEdge} strokeWidth="0.6"/>

        {/* greenhouse cluster — east */}
        <g stroke={C.greenhouseEdge} strokeWidth="0.8" fill={fillGreen}>
          <rect x="730" y="240" width="130" height="40"/>
          <rect x="730" y="285" width="130" height="40"/>
          <rect x="730" y="330" width="130" height="40"/>
          <rect x="730" y="375" width="130" height="25"/>
        </g>

        {/* village (north-west cluster) */}
        <g>
          <rect x="180" y="100" width="180" height="70" fill={C.village} stroke={C.villageEdge} strokeWidth="0.8"/>
          {/* buildings */}
          {[
            [188,108,28,18],[222,108,24,16],[252,108,30,18],[288,108,30,18],[324,108,30,18],
            [188,134,28,18],[222,134,24,16],[252,134,30,18],[288,134,30,18],[324,134,30,18],
            [188,156,28,12],[222,156,30,12],[256,156,30,12],[290,156,30,12],[324,156,30,12],
          ].map((b, i) => (
            <rect key={i} x={b[0]} y={b[1]} width={b[2]} height={b[3]}
                  fill={i % 3 === 0 ? C.bldgRoof : C.bldg}
                  stroke={C.villageEdge} strokeWidth="0.5"/>
          ))}
        </g>

        {/* small east settlement */}
        <g>
          <rect x="880" y="280" width="100" height="80" fill={C.village} stroke={C.villageEdge} strokeWidth="0.8"/>
          {[[886,288,18,12],[908,288,18,12],[930,288,22,12],[956,288,18,12],
            [886,304,22,12],[912,304,18,12],[934,304,22,12],[960,304,14,12],
            [886,320,18,16],[908,320,22,16],[934,320,18,16],[956,320,18,16],
            [886,340,22,14],[912,340,18,14],[934,340,22,14],[960,340,14,14]].map((b,i)=>(
            <rect key={i} x={b[0]} y={b[1]} width={b[2]} height={b[3]}
                  fill={i % 4 === 0 ? C.bldgRoof : C.bldg} stroke={C.villageEdge} strokeWidth="0.5"/>
          ))}
        </g>

        {/* ─── Roads ───────────────────────────────────────────── */}
        {/* main road — east-west (north of farmland) */}
        <g>
          <line x1="0" y1="200" x2="180" y2="200" stroke={C.roadMainEdge} strokeWidth="14"/>
          <line x1="0" y1="200" x2="180" y2="200" stroke={C.roadMain} strokeWidth="11"/>
          <line x1="360" y1="200" x2="1000" y2="200" stroke={C.roadMainEdge} strokeWidth="14"/>
          <line x1="360" y1="200" x2="1000" y2="200" stroke={C.roadMain} strokeWidth="11"/>
          {/* through village */}
          <line x1="180" y1="200" x2="360" y2="200" stroke={C.roadMainEdge} strokeWidth="14"/>
          <line x1="180" y1="200" x2="360" y2="200" stroke={C.roadMain} strokeWidth="11"/>
          {/* center dashes */}
          <line x1="0" y1="200" x2="1000" y2="200" stroke="#fff" strokeWidth="0.8" strokeDasharray="6 6" opacity="0.7"/>
        </g>

        {/* main road — north-south through east */}
        <g>
          <line x1="600" y1="0" x2="600" y2="200" stroke={C.roadMainEdge} strokeWidth="12"/>
          <line x1="600" y1="0" x2="600" y2="200" stroke={C.roadMain} strokeWidth="9"/>
          <line x1="600" y1="0" x2="600" y2="200" stroke="#fff" strokeWidth="0.8" strokeDasharray="6 6" opacity="0.6"/>
        </g>

        {/* sub road — between paddies */}
        <g>
          <line x1="0" y1="360" x2="1000" y2="360" stroke={C.roadSubEdge} strokeWidth="9"/>
          <line x1="0" y1="360" x2="1000" y2="360" stroke={C.roadSub} strokeWidth="6"/>
        </g>
        {/* dirt farm roads (perpendicular) */}
        <g>
          {[260, 410, 540, 670].map((x,i)=>(
            <g key={i}>
              <line x1={x} y1="360" x2={x} y2="490" stroke={C.roadDirtEdge} strokeWidth="5"/>
              <line x1={x} y1="360" x2={x} y2="490" stroke={C.roadDirt} strokeWidth="3"/>
            </g>
          ))}
          <line x1="260" y1="490" x2="780" y2="490" stroke={C.roadDirtEdge} strokeWidth="5"/>
          <line x1="260" y1="490" x2="780" y2="490" stroke={C.roadDirt} strokeWidth="3"/>
        </g>
        {/* dirt road north-south (village to main road south) */}
        <g>
          <line x1="270" y1="170" x2="270" y2="360" stroke={C.roadDirtEdge} strokeWidth="5"/>
          <line x1="270" y1="170" x2="270" y2="360" stroke={C.roadDirt} strokeWidth="3"/>
          <line x1="500" y1="200" x2="500" y2="360" stroke={C.roadDirtEdge} strokeWidth="5"/>
          <line x1="500" y1="200" x2="500" y2="360" stroke={C.roadDirt} strokeWidth="3"/>
          <line x1="800" y1="200" x2="800" y2="370" stroke={C.roadDirtEdge} strokeWidth="5"/>
          <line x1="800" y1="200" x2="800" y2="370" stroke={C.roadDirt} strokeWidth="3"/>
        </g>

        {/* rail line (south, parallel to river bank) */}
        <g>
          <line x1="0" y1="490" x2="1000" y2="490" stroke="transparent" strokeWidth="0"/>
        </g>

        {/* ─── Labels ──────────────────────────────────────────── */}
        {!simple && (
          <g style={{fontFamily: 'Pretendard GOV, Pretendard, sans-serif', pointerEvents: 'none'}}>
            <text x="220" y="142" fontSize="13" fill={C.label} fontWeight="600">매화동 마을회관</text>
            <text x="195" y="195" fontSize="11" fill={C.labelDim}>매화로</text>
            <text x="610" y="125" fontSize="11" fill={C.labelDim}>시흥남로</text>
            <text x="350" y="305" fontSize="12" fill={C.label} fontWeight="500">매화 들녘</text>
            <text x="755" y="265" fontSize="11" fill={C.label}>비닐하우스 단지</text>
            <text x="500" y="555" fontSize="13" fill="#5A7681" fontWeight="600" fontStyle="italic">매화천</text>
            <text x="50" y="350" fontSize="11" fill={C.labelDim}>마산 (해발 124m)</text>
            <text x="900" y="378" fontSize="11" fill={C.label}>월곶마을</text>
            <text x="305" y="225" fontSize="10" fill={C.labelDim}>매화초등학교</text>
          </g>
        )}

        {/* compass + scale (bottom-right) */}
        <g transform="translate(930, 30)">
          <circle r="14" fill="rgba(255,255,255,0.9)" stroke="#C8C8C8" strokeWidth="0.8"/>
          <path d="M0,-9 L4,4 L0,1 L-4,4 Z" fill="#D9534F"/>
          <path d="M0,9 L4,-4 L0,-1 L-4,-4 Z" fill="#7A7A7A" opacity="0.6"/>
          <text y="-18" textAnchor="middle" fontSize="8" fill="#666" fontFamily="Pretendard GOV, sans-serif">N</text>
        </g>
        <g transform="translate(820, 620)">
          <line x1="0" y1="0" x2="80" y2="0" stroke="#444" strokeWidth="2"/>
          <line x1="0" y1="-3" x2="0" y2="3" stroke="#444" strokeWidth="2"/>
          <line x1="40" y1="-2" x2="40" y2="2" stroke="#444" strokeWidth="1.5"/>
          <line x1="80" y1="-3" x2="80" y2="3" stroke="#444" strokeWidth="2"/>
          <text x="40" y="-7" textAnchor="middle" fontSize="9" fill="#444" fontFamily="Pretendard GOV, sans-serif">200 m</text>
        </g>
      </g>
    );
  }

  window.SuriMap = { Defs: MapDefs, Base: MapBase, COLORS: C };
})();
