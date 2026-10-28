import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import { ThemeProvider } from './context/ThemeContext';
import { AppProvider } from './context/AppContext';
import { MascotProvider } from './context/MascotContext';
import './styles/index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AppProvider>
        <MascotProvider>
          <App />
        </MascotProvider>
      </AppProvider>
    </ThemeProvider>
  </React.StrictMode>
);
