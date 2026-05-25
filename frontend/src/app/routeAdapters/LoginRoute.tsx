import { useLocation } from 'react-router-dom';

import { LoginPage } from '../../features/login/presentation/pages/LoginPage';
import { readLoginErrorMessage, readLoginRedirectPath } from '../loginRouteState';
import { ROUTES } from '../routes';

export function LoginRoute() {
  const location = useLocation();
  const loginRedirectPath = readLoginRedirectPath(location.state);
  const loginErrorMessage = readLoginErrorMessage(location.state);

  return <LoginPage redirectPath={loginRedirectPath ?? ROUTES.incidentList} initialErrorMessage={loginErrorMessage} />;
}
