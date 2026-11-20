import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../src/app/App';
import { config } from '../src/app/config';

describe('[MAM-19] web UI scaffold', () => {
  it('[MAM-19 AC-5] renders the frame with one h1, the USWDS banner and a main landmark', () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByText(/official website of the United States government/i)).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
  });

  it('[MAM-19 AC-7] reads the gateway and Keycloak addresses from configuration, with local defaults', () => {
    window.PEM_CONFIG = undefined;
    expect(config()).toEqual({
      gatewayUrl: 'http://localhost:8080',
      keycloakUrl: 'http://localhost:8180',
      realm: 'pem',
      clientId: 'pem-web',
    });
    window.PEM_CONFIG = { gatewayUrl: 'http://gw.example' };
    expect(config().gatewayUrl).toBe('http://gw.example');
    window.PEM_CONFIG = undefined;
  });
});
