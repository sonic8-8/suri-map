import { useEffect } from 'react';

export function useBrowserBackToIncidentList(
  onBrowserBackToIncidentList: (() => void) | undefined,
  enabled = true,
) {
  useEffect(() => {
    if (!enabled || !onBrowserBackToIncidentList) {
      return;
    }

    const handlePopState = () => {
      onBrowserBackToIncidentList();
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [enabled, onBrowserBackToIncidentList]);
}
