import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import 'maplibre-gl/dist/maplibre-gl.css';
import { App } from './app/App';
import { AppProviders } from './app/AppProviders';
import './shared/animations.css';
import './shared/tokens.css';
import './app/styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders>
      <App />
    </AppProviders>
  </StrictMode>,
);
