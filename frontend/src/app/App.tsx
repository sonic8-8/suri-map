import { AppRoutes } from './AppRoutes';
import { useAppSession } from './useAppSession';
import { useIncidentWorkspaceState } from './useIncidentWorkspaceState';
import { useMarkerNotificationQueue } from './useMarkerNotificationQueue';

export function App() {
  const session = useAppSession();
  const workspaceState = useIncidentWorkspaceState();
  const markerNotificationQueue = useMarkerNotificationQueue();

  return <AppRoutes session={session} workspaceState={workspaceState} markerNotificationQueue={markerNotificationQueue} />;
}
