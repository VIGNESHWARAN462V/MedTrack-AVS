import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'bootstrap/dist/css/bootstrap.min.css'
import './index.css'
import App from './App.jsx'

// Activate full application stylesheet cleanly right before React mounts
if (typeof document !== 'undefined') {
  const appCssLink = document.querySelector('link[data-app-css]');
  if (appCssLink && appCssLink.media !== 'all') {
    appCssLink.media = 'all';
  }
}

const container = document.getElementById('root');
if (container) {
  createRoot(container).render(
    <StrictMode>
      <App />
    </StrictMode>
  );
}
