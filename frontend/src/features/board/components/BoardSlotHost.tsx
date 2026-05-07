import { Component, createElement, type ComponentType, type ReactNode } from 'react';
import {
  s3_2BoardSlotRegistryContract,
  type BoardSlotName,
  type BoardSlotRegistryEntry,
} from '../contracts/boardSlotRegistryContract';

type BoardSlotCursor = {
  readonly id: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: string;
  readonly sourceHash: string;
  readonly latestEventId: string;
};

export type BoardSlotRow = BoardSlotCursor & {
  readonly slot: BoardSlotName;
  readonly slotSources: readonly string[];
  readonly sourceVersions: Readonly<Record<string, number>>;
  readonly sourceHashes: Readonly<Record<string, string>>;
};

export type BoardSlotRendererProps = {
  readonly row: BoardSlotRow;
};

export type BoardSlotHostProps = {
  readonly registry: readonly BoardSlotRegistryEntry[];
  readonly rows: readonly BoardSlotRow[];
  readonly renderers: Partial<Record<BoardSlotName, ComponentType<BoardSlotRendererProps>>>;
};

export function BoardSlotHost({ registry, rows, renderers }: BoardSlotHostProps) {
  assertCanonicalRegistry(registry);

  const registrySlots = registry.map((entry) => entry.slot);
  const registrySlotSet = new Set<string>(registrySlots);
  const rowsBySlot = new Map(rows.map((row) => [row.slot, row]));

  for (const row of rows) {
    if (!registrySlotSet.has(row.slot)) {
      throw new Error(`Unknown board slot row: ${row.slot}`);
    }
  }

  return (
    <>
      {registrySlots.map((slot) => {
        const row = rowsBySlot.get(slot as BoardSlotName);
        const Renderer = renderers[slot as BoardSlotName];

        return (
          <section key={slot} data-testid="board-slot" data-slot={slot}>
            {row && Renderer ? (
              <SlotErrorBoundary slot={slot}>{createElement(Renderer, { row })}</SlotErrorBoundary>
            ) : (
              <div data-testid={`slot-failure-${slot}`}>slot renderer unavailable</div>
            )}
          </section>
        );
      })}
    </>
  );
}

function assertCanonicalRegistry(registry: readonly BoardSlotRegistryEntry[]) {
  const expectedSlots = s3_2BoardSlotRegistryContract.map((entry) => entry.slot);
  const actualSlots = registry.map((entry) => entry.slot);

  if (actualSlots.length !== expectedSlots.length) {
    throw new Error(`Board slot registry must match spec/boundaries.md §9.2: ${actualSlots.join(',')}`);
  }

  for (const [index, slot] of actualSlots.entries()) {
    if (slot !== expectedSlots[index]) {
      throw new Error(`Board slot registry must match spec/boundaries.md §9.2: ${slot}`);
    }
  }
}

type SlotErrorBoundaryProps = {
  readonly slot: string;
  readonly children: ReactNode;
};

type SlotErrorBoundaryState = {
  readonly errorMessage: string | null;
};

class SlotErrorBoundary extends Component<SlotErrorBoundaryProps, SlotErrorBoundaryState> {
  readonly state: SlotErrorBoundaryState = {
    errorMessage: null,
  };

  static getDerivedStateFromError(error: unknown): SlotErrorBoundaryState {
    return {
      errorMessage: error instanceof Error ? error.message : 'slot renderer failed',
    };
  }

  render() {
    if (this.state.errorMessage) {
      return <div data-testid={`slot-failure-${this.props.slot}`}>{this.state.errorMessage}</div>;
    }

    return this.props.children;
  }
}
