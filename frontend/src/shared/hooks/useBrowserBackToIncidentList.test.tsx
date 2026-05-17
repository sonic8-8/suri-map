import { render } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

import { useBrowserBackToIncidentList } from './useBrowserBackToIncidentList';

function TestHarness({
  enabled = true,
  onBrowserBackToIncidentList,
}: {
  enabled?: boolean;
  onBrowserBackToIncidentList?: () => void;
}) {
  useBrowserBackToIncidentList(onBrowserBackToIncidentList, enabled);
  return null;
}

describe('useBrowserBackToIncidentList', () => {
  test('invokes the incident list callback when the browser back button is used', () => {
    const onBrowserBackToIncidentList = vi.fn();

    render(<TestHarness onBrowserBackToIncidentList={onBrowserBackToIncidentList} />);

    window.dispatchEvent(new PopStateEvent('popstate'));

    expect(onBrowserBackToIncidentList).toHaveBeenCalledTimes(1);
  });

  test('does not register a listener when disabled', () => {
    const onBrowserBackToIncidentList = vi.fn();

    render(<TestHarness enabled={false} onBrowserBackToIncidentList={onBrowserBackToIncidentList} />);

    window.dispatchEvent(new PopStateEvent('popstate'));

    expect(onBrowserBackToIncidentList).not.toHaveBeenCalled();
  });
});
