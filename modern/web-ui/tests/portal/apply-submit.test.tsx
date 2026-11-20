// MAM-64 AC-2, AC-3 and AC-4: a valid application is accepted (journey J1), an empty taxonomy gives one E104 beside the
// field and in the summary (journey J2), and E101 / E102 / E103 appear at their own field, once per failed check.
// The enrollment service is replaced by the recording fetch stub; the page must show exactly what it answers (D-001, D-016).
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { response } from '../support/fakeApi';
import { application, APPLICATION_ID, applicationPage } from '../support/fixtures';
import {
  control,
  expectErrorAt,
  fieldError,
  fillIn,
  LIST_ROUTE,
  MESSAGE_OF,
  occurrences,
  openApplyForm,
  rejection,
  rivera,
  sentBody,
  submissions,
  submit,
  SUBMIT_ROUTE,
  summaryLinks,
  type ErrorCode,
} from '../support/applyForm';
import { defaultRoutes, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const CREATED = application({
  status: 'VALIDATED',
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
});

/** The service accepts the application; from then on the list of the provider contains it and GET by id returns it (MAM-66 AC-1, Q-097). */
function acceptingRoutes() {
  let created = false;
  return {
    [SUBMIT_ROUTE]: () => {
      created = true;
      return response(201, CREATED);
    },
    [LIST_ROUTE]: () => response(200, applicationPage(created ? [CREATED] : [])),
    [`GET /api/v1/applications/${APPLICATION_ID}`]: () => (created ? response(200, CREATED) : response(404, { title: 'Not Found', status: 404 })),
  };
}

describe('[MAM-64 AC-2] a valid application is accepted (journey J1)', () => {
  it('[MAM-64 AC-2] Submit application sends one POST /api/v1/applications with providerType IN and the individual details', async () => {
    const { user } = await openApplyForm(acceptingRoutes());
    await fillIn(user, rivera());
    await submit(user);

    await waitFor(() => expect(submissions()).toHaveLength(1));
    expect(sentBody(submissions()[0])).toEqual({
      npi: '1003000111',
      medicaidId: 'TX0000111',
      providerType: 'IN',
      taxonomy: '207Q00000X',
      individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
    });
  });

  it('[MAM-64 AC-2] the request is a JSON POST to the gateway that carries the bearer token of provider1', async () => {
    const { user } = await openApplyForm(acceptingRoutes());
    await fillIn(user, rivera());
    await submit(user);

    await waitFor(() => expect(submissions()).toHaveLength(1));
    const call = submissions()[0];
    expect(call?.url.origin).toBe('http://localhost:8080');
    expect(call?.headers.get('content-type')).toMatch(/application\/json/);
    expect(call?.headers.get('authorization')).toMatch(/^Bearer \S+/);
  });

  it('[MAM-64 AC-2] after the 201 the page opens /portal/applications/:applicationId and shows Validated', async () => {
    const { user, path } = await openApplyForm(acceptingRoutes());
    await fillIn(user, rivera());
    await submit(user);

    expect(await screen.findByRole('heading', { level: 1, name: 'Application status' })).toBeInTheDocument();
    expect(path()).toBe(`/portal/applications/${APPLICATION_ID}`);
    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
  });

  it('[MAM-64 AC-2] the status page of the new application shows Validated even when the home page was opened first and listed no application', async () => {
    signedInAs('provider1');
    installApi({ ...defaultRoutes(), ...acceptingRoutes() });
    const { user, path } = renderApp('/portal');
    expect(await screen.findByText('No applications yet')).toBeInTheDocument();

    await user.click(within(screen.getByRole('main')).getByRole('link', { name: 'Apply for enrolment' }));
    await screen.findByRole('button', { name: 'Submit application' });
    await fillIn(user, rivera());
    await submit(user);

    expect(await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated')).toBeInTheDocument();
    expect(path()).toBe(`/portal/applications/${APPLICATION_ID}`);
    expect(screen.queryByText(/not found/i)).toBeNull();
  });

  it('[MAM-64 AC-2] no error summary and no field error is shown for an accepted application', async () => {
    const { user } = await openApplyForm(acceptingRoutes());
    await fillIn(user, rivera());
    await submit(user);

    await within(await screen.findByRole('region', { name: 'Application' })).findByText('Validated');
    expect(summaryLinks()).toHaveLength(0);
    expect(screen.queryByText(/error in the application/)).toBeNull();
  });

  it('[MAM-64 AC-2] a group is sent with providerType GR and its organisation details, without individual details', async () => {
    const { user } = await openApplyForm(acceptingRoutes());
    await fillIn(user, {
      providerType: 'GR',
      npi: '1003000222',
      medicaidId: 'TX0000222',
      taxonomy: '193200000X',
      legalName: 'RIO GRANDE FAMILY MEDICINE PLLC',
      dbaName: 'RIO GRANDE FAMILY CARE',
      ownerNpi: '1003000111',
    });
    await submit(user);

    await waitFor(() => expect(submissions()).toHaveLength(1));
    expect(sentBody(submissions()[0])).toEqual({
      npi: '1003000222',
      medicaidId: 'TX0000222',
      providerType: 'GR',
      taxonomy: '193200000X',
      organization: { legalName: 'RIO GRANDE FAMILY MEDICINE PLLC', dbaName: 'RIO GRANDE FAMILY CARE', ownerNpi: '1003000111' },
    });
  });
});

describe('[MAM-64 AC-3] an empty taxonomy gives one error E104 beside the field and in the summary (journey J2)', () => {
  const E104 = fieldError('E104');

  async function submitWithoutTaxonomy() {
    const rendered = await openApplyForm({ [SUBMIT_ROUTE]: rejection(E104) });
    await fillIn(rendered.user, rivera({ taxonomy: '' }));
    await submit(rendered.user);
    await screen.findByRole('heading', { level: 2, name: '1 error in the application' });
    return rendered;
  }

  it('[MAM-64 AC-3] the request is sent although the taxonomy is empty (the browser does not block the submit)', async () => {
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: rejection(E104) });
    await fillIn(user, rivera({ taxonomy: '' }));
    await submit(user);

    await waitFor(() => expect(submissions()).toHaveLength(1));
    const body = sentBody(submissions()[0]);
    expect(body['taxonomy'] ?? '').toBe('');
    expect(body['npi']).toBe('1003000111');
    expect(body['providerType']).toBe('IN');
  });

  it('[MAM-64 AC-3] an error summary "1 error in the application" is shown at the top of the form', async () => {
    await submitWithoutTaxonomy();

    const heading = screen.getByRole('heading', { level: 2, name: '1 error in the application' });
    expect(heading.compareDocumentPosition(control('npi')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(screen.getByRole('main')).getByRole('heading', { level: 1 }).compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('[MAM-64 AC-3] the summary has one link "E104: <message of the API>"', async () => {
    await submitWithoutTaxonomy();

    const links = summaryLinks();
    expect(links).toHaveLength(1);
    expect(text(links[0])).toBe(`E104: ${MESSAGE_OF.E104}`);
  });

  it('[MAM-64 AC-3] choosing the summary link moves the focus to the Taxonomy code field', async () => {
    const { user } = await submitWithoutTaxonomy();

    await user.click(summaryLinks()[0]!);

    expect(control('taxonomy')).toHaveFocus();
  });

  it('[MAM-64 AC-3] the same code and message are shown next to the Taxonomy code field', async () => {
    await submitWithoutTaxonomy();

    expectErrorAt('taxonomy', E104);
    expect(occurrences('E104')).toEqual({ inSummary: 1, atFields: 1 });
  });

  it('[MAM-64 AC-3] the other fields show no error', async () => {
    await submitWithoutTaxonomy();

    for (const name of ['npi', 'medicaidId', 'providerType', 'lastName', 'firstName', 'licenseNo', 'licenseState'] as const) {
      expect(control(name)).not.toHaveAttribute('aria-invalid', 'true');
    }
  });

  it('[MAM-64 AC-3] the page stays on /portal/apply and keeps everything that was typed', async () => {
    const { path } = await submitWithoutTaxonomy();

    expect(path()).toBe('/portal/apply');
    expect(screen.getByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeInTheDocument();
    const typed = { npi: '1003000111', medicaidId: 'TX0000111', taxonomy: '', lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' };
    for (const [name, value] of Object.entries(typed)) expect(control(name as keyof typeof typed)).toHaveValue(value);
    expect(control('providerType')).toHaveValue('IN');
  });

  it('[MAM-64 AC-3] no success page is shown', async () => {
    await submitWithoutTaxonomy();

    expect(screen.queryByText('Validated')).toBeNull();
    expect(screen.queryByRole('heading', { level: 1, name: 'Application status' })).toBeNull();
  });

  it('[MAM-64 AC-3] the message is the one the API returned, not a text of the page', async () => {
    const own = fieldError('E104', 'Taxonomy is missing (message of this test)');
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: rejection(own) });
    await fillIn(user, rivera({ taxonomy: '' }));
    await submit(user);

    await screen.findByRole('heading', { level: 2, name: '1 error in the application' });
    expect(text(summaryLinks()[0])).toBe('E104: Taxonomy is missing (message of this test)');
    expectErrorAt('taxonomy', own);
  });
});

describe('[MAM-64 AC-4] E101, E102 and E103 appear at their own field, once per failed check', () => {
  async function submitAndGet(values: Parameters<typeof fillIn>[1], ...errors: ReturnType<typeof fieldError>[]) {
    const rendered = await openApplyForm({ [SUBMIT_ROUTE]: rejection(...errors) });
    await fillIn(rendered.user, values);
    await submit(rendered.user);
    await screen.findByRole('heading', { level: 2, name: `${errors.length} ${errors.length === 1 ? 'error' : 'errors'} in the application` });
    return rendered;
  }

  it('[MAM-64 AC-4] (a) a blank NPI is sent, and E101 is shown next to NPI and once in the summary', async () => {
    await submitAndGet(rivera({ npi: '' }), fieldError('E101'));

    expect(sentBody(submissions()[0])['npi']).toBe('');
    expectErrorAt('npi', fieldError('E101'));
    expect(occurrences('E101')).toEqual({ inSummary: 1, atFields: 1 });
    expect(summaryLinks().map((link) => text(link))).toEqual([`E101: ${MESSAGE_OF.E101}`]);
  });

  it('[MAM-64 AC-4] (b) no provider type chosen is sent, and E102 is shown next to Provider type and once in the summary', async () => {
    await submitAndGet(rivera({ providerType: '' }), fieldError('E102'));

    expect(sentBody(submissions()[0])['providerType'] ?? '').toBe('');
    expectErrorAt('providerType', fieldError('E102'));
    expect(occurrences('E102')).toEqual({ inSummary: 1, atFields: 1 });
    expect(summaryLinks().map((link) => text(link))).toEqual([`E102: ${MESSAGE_OF.E102}`]);
  });

  it('[MAM-64 AC-4] (c) an NPI that already has a VALIDATED application shows E103 next to NPI and once in the summary', async () => {
    await submitAndGet(rivera(), fieldError('E103'));

    expectErrorAt('npi', fieldError('E103'));
    expect(occurrences('E103')).toEqual({ inSummary: 1, atFields: 1 });
    expect(summaryLinks().map((link) => text(link))).toEqual([`E103: ${MESSAGE_OF.E103}`]);
  });

  it('[MAM-64 AC-4] (d) no provider type and an empty taxonomy give the heading "2 errors in the application" with E102 and E104 listed once each', async () => {
    await submitAndGet(rivera({ providerType: '', taxonomy: '' }), fieldError('E102'), fieldError('E104'));

    expect(screen.getByRole('heading', { level: 2, name: '2 errors in the application' })).toBeInTheDocument();
    expect(summaryLinks().map((link) => text(link))).toEqual([`E102: ${MESSAGE_OF.E102}`, `E104: ${MESSAGE_OF.E104}`]);
    expect(occurrences('E102')).toEqual({ inSummary: 1, atFields: 1 });
    expect(occurrences('E104')).toEqual({ inSummary: 1, atFields: 1 });
  });

  it('[MAM-64 AC-4] (d) E102 is next to Provider type and E104 next to Taxonomy code', async () => {
    await submitAndGet(rivera({ providerType: '', taxonomy: '' }), fieldError('E102'), fieldError('E104'));

    expectErrorAt('providerType', fieldError('E102'));
    expectErrorAt('taxonomy', fieldError('E104'));
  });

  it.each<[ErrorCode, 'npi' | 'providerType' | 'taxonomy']>([
    ['E101', 'npi'],
    ['E102', 'providerType'],
    ['E103', 'npi'],
    ['E104', 'taxonomy'],
  ])('[MAM-64 AC-4] the summary link of %s moves the focus to its field (%s)', async (code, field) => {
    const { user } = await submitAndGet(rivera(), fieldError(code));

    await user.click(summaryLinks()[0]!);

    expect(control(field)).toHaveFocus();
  });

  it('[MAM-64 AC-4] a second rejection replaces the first one: the old errors are not listed again', async () => {
    const answers = [rejection(fieldError('E104')), rejection(fieldError('E102'), fieldError('E104'))];
    const { user } = await openApplyForm({ [SUBMIT_ROUTE]: (call) => answers.shift()!(call) });
    await fillIn(user, rivera({ taxonomy: '' }));
    await submit(user);
    await screen.findByRole('heading', { level: 2, name: '1 error in the application' });

    await fillIn(user, { providerType: '' });
    await submit(user);

    await screen.findByRole('heading', { level: 2, name: '2 errors in the application' });
    expect(screen.queryByRole('heading', { level: 2, name: '1 error in the application' })).toBeNull();
    expect(occurrences('E104')).toEqual({ inSummary: 1, atFields: 1 });
    expect(submissions()).toHaveLength(2);
  });

  it('[MAM-64 AC-4] an error for a field the form does not have is still listed in the summary (nothing is swallowed)', async () => {
    const unknown = { code: 'E103', field: 'application', message: 'An application for this NPI has already been accepted.' } as const;
    await submitAndGet(rivera(), unknown);

    expect(screen.getByText(/E103: An application for this NPI has already been accepted\./)).toBeInTheDocument();
  });
});
