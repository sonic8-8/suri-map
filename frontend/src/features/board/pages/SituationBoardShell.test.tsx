import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, test, vi, type Mock } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { SituationBoardPage } from './SituationBoardPage';
import { useBoardDisplayStore } from '../model/boardDisplayStore';

type MockMapOptions = {
  readonly style?: unknown;
  readonly center?: unknown;
  readonly zoom?: unknown;
  readonly container?: unknown;
};

type MockMapInstance = {
  readonly options: MockMapOptions;
  readonly addControl: Mock;
  readonly remove: Mock;
};

const maplibreMock = vi.hoisted(() => ({
  mapInstances: [] as MockMapInstance[],
}));

vi.mock('maplibre-gl', () => {
  class MockMap implements MockMapInstance {
    readonly addControl = vi.fn();
    readonly remove = vi.fn();

    constructor(readonly options: MockMapOptions) {
      maplibreMock.mapInstances.push(this);
    }
  }

  class MockNavigationControl {
    constructor(readonly options: unknown) {}
  }

  class MockAttributionControl {
    constructor(readonly options: unknown) {}
  }

  return {
    default: {
      Map: MockMap,
      NavigationControl: MockNavigationControl,
      AttributionControl: MockAttributionControl,
    },
    Map: MockMap,
    NavigationControl: MockNavigationControl,
    AttributionControl: MockAttributionControl,
  };
});

describe('L6-T01A situation board shell', () => {
  afterEach(() => {
    maplibreMock.mapInstances.length = 0;
    act(() => {
      useBoardDisplayStore.setState({
        incidentId: 'inc-precinct-first-001',
        selectedOpIds: [],
        visibleLayers: {
          path: true,
          area: true,
          marker: true,
          op_history: true,
        },
        viewMode: 'standard',
      });
    });
  });

  test('renders route-owned shell without source-owner placeholder data', () => {
    const { container } = renderSituationBoard('/incidents/inc-shell-route-001/board');

    expect(screen.getByRole('main')).toHaveClass('app-shell');
    expect(screen.getByLabelText('상황판')).toBeInTheDocument();
    expect(screen.getByText('inc-shell-route-001')).toBeInTheDocument();
    expect(screen.queryByText('반포 한강공원 일대')).not.toBeInTheDocument();
    expect(screen.queryByText('온라인 단말')).not.toBeInTheDocument();
    expect(screen.queryByText('미전송 큐')).not.toBeInTheDocument();
    expect(container.querySelector('.route-line')).toBeNull();
    expect(container.querySelector('.marker')).toBeNull();
  });

  test('wires route incidentId into S3-2 shared display state', async () => {
    renderSituationBoard('/incidents/inc-shared-state-001/board');

    await waitFor(() => {
      expect(useBoardDisplayStore.getState().incidentId).toBe('inc-shared-state-001');
    });
    expect(useBoardDisplayStore.getState().selectedOpIds).toEqual([]);
  });

  test('keeps selected OP display state detached from source-owner arrays', () => {
    const selectedOpIds = ['op-display-only-001'];

    act(() => {
      useBoardDisplayStore.getState().setSelectedOpIds(selectedOpIds);
    });
    selectedOpIds.push('op-source-owner-mutation');

    expect(useBoardDisplayStore.getState().selectedOpIds).toEqual(['op-display-only-001']);
  });

  test('initializes MapLibre root with the S7 local style URL', async () => {
    renderSituationBoard('/incidents/inc-map-root-001/board');

    await waitFor(() => {
      expect(maplibreMock.mapInstances).toHaveLength(1);
    });
    expect(maplibreMock.mapInstances[0]?.options.style).toBe('/tiles/styles/osm-local.json');
    expect(maplibreMock.mapInstances[0]?.options.center).toEqual([126.9565, 37.5712]);
    expect(maplibreMock.mapInstances[0]?.options.zoom).toBe(13);
    expect(screen.getByLabelText('MapLibre board map inc-map-root-001')).toBeInTheDocument();
  });
});

function renderSituationBoard(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/incidents/:incidentId/board" element={<SituationBoardPage />} />
      </Routes>
    </MemoryRouter>,
  );
}
