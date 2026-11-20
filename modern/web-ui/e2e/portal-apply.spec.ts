// MAM-64 end to end: provider1 signs in with the real Keycloak realm pem and applies for enrolment at /portal/apply in a
// real browser. The enrollment and provider services are answered in the browser (e2e/support/gateway-routes.ts) with
// the answers of their contracts, so that the journey does not depend on data in the stack: 201 VALIDATED, 422 with
// E101-E104, 400 / 401 / 403 / 5xx and a network failure. Every state is scanned with axe (AC-7).
import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, sequence, type MockApi, type RouteHandler } from './support/gateway-routes';
import { signIn } from './support/keycloak';
import { expectNoAxeViolations, focusIsVisible, tabTo } from './support/page';

// The Keycloak form is filled with the test user's password: keep it out of Playwright traces.
test.use({ trace: 'off' });

const APPLICATION_ID = '0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11';
const NPI = '1003000111';
/** MAM-66 AC-1 (Q-097): the status page reads GET /api/v1/applications/{id} (MAM-54); the stubs answer it as they answer the list. */
const BY_ID = `GET /api/v1/applications/${APPLICATION_ID}`;

const RIVERA = {
  npi: NPI,
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  lastName: 'RIVERA',
  firstName: 'MARIA',
  licenseNo: 'MD12345',
  licenseState: 'TX',
};

const LABEL = {
  npi: /^NPI/,
  medicaidId: /^Medicaid ID/,
  providerType: /^Provider type/,
  taxonomy: /^Taxonomy code/,
  lastName: /^Last name/,
  firstName: /^First name/,
  licenseNo: /^Licence number/,
  licenseState: /^Licence state/,
  legalName: /^Legal name/,
  dbaName: /^DBA name/,
  ownerNpi: /^Owner NPI/,
} as const;
type Field = keyof typeof LABEL;

const ERRORS = {
  E101: { code: 'E101', field: 'npi', message: 'Enter the 10-digit NPI.' },
  E102: { code: 'E102', field: 'providerType', message: 'The provider type must be IN, GR or FA.' },
  E103: { code: 'E103', field: 'npi', message: 'An application for this NPI has already been accepted.' },
  E104: { code: 'E104', field: 'taxonomy', message: 'Enter the taxonomy code.' },
} as const;
type ErrorCode = keyof typeof ERRORS;
const FIELD_OF: Record<ErrorCode, Field> = { E101: 'npi', E102: 'providerType', E103: 'npi', E104: 'taxonomy' };

const VALIDATED = {
  applicationId: APPLICATION_ID,
  status: 'VALIDATED',
  npi: NPI,
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
  source: 'PORTAL',
  submittedAt: '2026-03-01T10:00:00Z',
};
const REJECTED = { ...VALIDATED, status: 'REJECTED', taxonomy: '', errors: [ERRORS.E104] };

const page_of = (items: unknown[]) => ({ items, page: 0, size: 20, total: items.length });

function rejection(...codes: ErrorCode[]) {
  const errors = codes.map((code) => ERRORS[code]);
  return json(422, {
    title: 'Unprocessable Entity',
    status: 422,
    detail: 'The application failed validation.',
    ...(errors.length === 1 ? { code: errors[0]?.code } : {}),
    errors,
  });
}

/** The accessible description of a field holds its hint and its error: match the error inside it. */
const described = (code: ErrorCode) => new RegExp(`${code}: ${ERRORS[code].message.replaceAll('.', '\\.')}`);

const field = (page: Page, name: Field) => page.getByLabel(LABEL[name]);
const summaryHeading = (page: Page, text: string) => page.getByRole('heading', { level: 2, name: text });
const summaryLinks = (page: Page) => page.getByRole('main').getByRole('link', { name: /^E\d{3}: / });
/** The "Application" summary list (region) of the status page: the status badge is looked up inside it, because the step indicator above it names step 2 "Validated" too (Q-104, UI polish). */
const applicationRegion = (page: Page) => page.getByRole('region', { name: 'Application', exact: true });

/** Routes of the portal pages that the journeys visit after the form. */
function portalRoutes(extra: Record<string, RouteHandler> = {}): Record<string, RouteHandler> {
  return {
    'GET /api/v1/applications': () => json(200, page_of([])),
    [`GET /api/v1/providers/${NPI}`]: () => json(404, { title: 'Not Found', status: 404 }),
    ...extra,
  };
}

async function openApply(page: Page, routes: Record<string, RouteHandler>): Promise<MockApi> {
  const api = await mockApi(page, portalRoutes(routes));
  await signIn(page, 'provider1', '/portal/apply');
  await expect(page.getByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Submit application' })).toBeVisible();
  return api;
}

async function fillIn(page: Page, values: Partial<Record<Field, string>>): Promise<void> {
  if (values.providerType !== undefined) await field(page, 'providerType').selectOption(values.providerType);
  for (const [name, value] of Object.entries(values) as Array<[Field, string]>) {
    if (name !== 'providerType') await field(page, name).fill(value);
  }
}

const submit = (page: Page) => page.getByRole('button', { name: 'Submit application' }).click();

/** True when the keyboard focus is in the error summary that has this heading (not on the form, not on the page body). */
async function focusIsInSummary(page: Page, heading: string): Promise<boolean> {
  return page.evaluate((expected) => {
    const active = document.activeElement;
    if (!active || active === document.body || active.querySelector('form') || active.matches('main')) return false;
    const h2 = active.matches('h2') ? active : active.querySelector('h2');
    return (h2?.textContent ?? '').trim() === expected;
  }, heading);
}

test.describe('[MAM-64 AC-1] the form shows every field with a visible label', () => {
  test('[MAM-64 AC-1] provider1 sees the labelled fields, the type list and the Submit application button', async ({ page }) => {
    await openApply(page, {});

    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    for (const name of ['npi', 'medicaidId', 'providerType', 'taxonomy', 'lastName', 'firstName', 'licenseNo', 'licenseState'] as const) {
      await expect(field(page, name)).toBeVisible();
    }
    const options = (await field(page, 'providerType').locator('option').allTextContents()).map((option) => option.trim());
    expect(options).toEqual(['Choose...', 'Individual (IN)', 'Group (GR)', 'Facility (FA)']);
    await expect(field(page, 'npi')).toHaveAttribute('maxlength', '10');
    await expect(field(page, 'medicaidId')).toHaveAttribute('maxlength', '9');
    await expect(field(page, 'taxonomy')).toHaveAttribute('maxlength', '10');
    await expect(field(page, 'licenseState')).toHaveAttribute('maxlength', '2');
    await expect(page.getByRole('button', { name: 'Submit application' })).toBeVisible();
  });

  for (const [label, value] of [['Group', 'GR'], ['Facility', 'FA']] as const) {
    test(`[MAM-64 AC-1] choosing ${label} replaces the individual inputs by Legal name, DBA name and Owner NPI`, async ({ page }) => {
      await openApply(page, {});

      await field(page, 'providerType').selectOption(value);

      for (const name of ['legalName', 'dbaName', 'ownerNpi'] as const) await expect(field(page, name)).toBeVisible();
      for (const name of ['lastName', 'firstName', 'licenseNo', 'licenseState'] as const) await expect(field(page, name)).toHaveCount(0);
      await expect(field(page, 'legalName')).toHaveAttribute('maxlength', '60');
      await expect(field(page, 'dbaName')).toHaveAttribute('maxlength', '30');
      await expect(field(page, 'ownerNpi')).toHaveAttribute('maxlength', '10');
    });
  }
});

test.describe('[MAM-64 AC-2] a valid application is accepted (journey J1)', () => {
  test('[MAM-64 AC-2] provider1 submits the application of RIVERA MARIA: POST with providerType IN, then the status page shows Validated', async ({ page }) => {
    let created = false;
    const api = await openApply(page, {
      'POST /api/v1/applications': () => {
        created = true;
        return json(201, VALIDATED);
      },
      'GET /api/v1/applications': () => json(200, page_of(created ? [VALIDATED] : [])),
      [BY_ID]: () => (created ? json(200, VALIDATED) : json(404, { title: 'Not Found', status: 404 })),
    });

    await fillIn(page, RIVERA);
    await submit(page);

    await expect(page).toHaveURL(new RegExp(`/portal/applications/${APPLICATION_ID}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'Application status' })).toBeVisible();
    await expect(applicationRegion(page).getByText('Validated')).toBeVisible();
    const posts = api.to('POST', '/api/v1/applications');
    expect(posts).toHaveLength(1);
    expect(posts[0]?.body).toEqual({
      npi: NPI,
      medicaidId: 'TX0000111',
      providerType: 'IN',
      taxonomy: '207Q00000X',
      individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
    });
    expect(posts[0]?.authorization).toMatch(/^Bearer \S+/);
    expect(posts[0]?.contentType).toMatch(/application\/json/);
  });
});

test.describe('[MAM-64 AC-3] an empty taxonomy gives one error E104 beside the field and in the summary (journey J2)', () => {
  test('[MAM-64 AC-3] the request is sent with the empty taxonomy; the summary links to the field, the same error is next to it, and nothing typed is lost', async ({ page }) => {
    const api = await openApply(page, { 'POST /api/v1/applications': () => rejection('E104') });

    await fillIn(page, { ...RIVERA, taxonomy: '' });
    await submit(page);

    await expect(summaryHeading(page, '1 error in the application')).toBeVisible();
    expect(api.to('POST', '/api/v1/applications')).toHaveLength(1);
    expect((api.to('POST', '/api/v1/applications')[0]?.body as Record<string, unknown>)['taxonomy'] ?? '').toBe('');
    await expect(page).toHaveURL(/\/portal\/apply$/);
    await expect(summaryLinks(page)).toHaveText([`E104: ${ERRORS.E104.message}`]);
    await expect(field(page, 'taxonomy')).toHaveAccessibleDescription(described('E104'));
    await expect(page.getByText(/\bE104:/)).toHaveCount(2); // once in the summary, once at the field
    for (const [name, value] of Object.entries(RIVERA) as Array<[Field, string]>) await expect(field(page, name)).toHaveValue(name === 'taxonomy' ? '' : value);
    await expect(page.getByRole('main').getByText('Validated')).toHaveCount(0);

    await summaryLinks(page).first().click();
    await expect(field(page, 'taxonomy')).toBeFocused();
  });
});

test.describe('[MAM-64 AC-4] E101, E102 and E103 appear at their own field, once per failed check', () => {
  const cases: Array<{ name: string; values: Partial<Record<Field, string>>; codes: ErrorCode[] }> = [
    { name: '(a) a blank NPI', values: { npi: '' }, codes: ['E101'] },
    { name: '(b) no provider type', values: { providerType: '' }, codes: ['E102'] },
    { name: '(c) an NPI with a VALIDATED application', values: {}, codes: ['E103'] },
    { name: '(d) no provider type and an empty taxonomy', values: { providerType: '', taxonomy: '' }, codes: ['E102', 'E104'] },
  ];
  for (const { name, values, codes } of cases) {
    test(`[MAM-64 AC-4] ${name}: ${codes.join(' and ')} next to the field and once in the summary, codes in full`, async ({ page }) => {
      const api = await openApply(page, { 'POST /api/v1/applications': () => rejection(...codes) });

      await fillIn(page, { ...RIVERA, ...values });
      await submit(page);

      await expect(summaryHeading(page, `${codes.length} ${codes.length === 1 ? 'error' : 'errors'} in the application`)).toBeVisible();
      expect(api.to('POST', '/api/v1/applications')).toHaveLength(1);
      await expect(summaryLinks(page)).toHaveText(codes.map((code) => `${code}: ${ERRORS[code].message}`));
      for (const code of codes) {
        await expect(field(page, FIELD_OF[code])).toHaveAccessibleDescription(described(code));
        await expect(page.getByText(new RegExp(`\\b${code}:`))).toHaveCount(2);
      }
    });
  }
});

test.describe('[MAM-64 AC-5] a rejected application can be found afterwards and nothing is registered', () => {
  test('[MAM-64 AC-5] after the E104 rejection: My applications lists it as Rejected with a View link, the status page shows it, My provider record says that no record exists', async ({ page }) => {
    await openApply(page, {
      'POST /api/v1/applications': () => rejection('E104'),
      'GET /api/v1/applications': () => json(200, page_of([REJECTED])),
      [BY_ID]: () => json(200, REJECTED),
    });
    await fillIn(page, { ...RIVERA, taxonomy: '' });
    await submit(page);
    await expect(summaryHeading(page, '1 error in the application')).toBeVisible();

    await page.getByRole('main').getByRole('link', { name: 'My applications' }).click();

    await expect(page).toHaveURL(/\/portal$/);
    const row = page.getByRole('row').filter({ hasText: NPI });
    await expect(row.getByRole('cell').nth(2)).toHaveText('Rejected');
    await row.getByRole('link', { name: /^View/ }).click();
    await expect(page).toHaveURL(new RegExp(`/portal/applications/${APPLICATION_ID}$`));
    await expect(applicationRegion(page).getByText('Rejected')).toBeVisible();

    await page.getByRole('navigation', { name: 'Primary navigation' }).getByRole('link', { name: 'My provider record' }).click();
    await expect(page).toHaveURL(/\/portal\/provider$/);
    await expect(page.getByRole('heading', { level: 1, name: 'My provider record' })).toBeVisible();
    await expect(page.getByRole('main').getByText(/No provider record exists yet/i)).toBeVisible();
  });
});

test.describe('[MAM-64 AC-6] other failures are shown, never swallowed', () => {
  const failures: Array<{ name: string; handler: RouteHandler; reason: RegExp }> = [
    { name: '400', handler: () => json(400, { title: 'Bad Request', status: 400, detail: 'licenseState must be 2 letters (detail sent by this test)' }), reason: /licenseState must be 2 letters \(detail sent by this test\)/ },
    { name: '401', handler: () => json(401, { title: 'Not authenticated', status: 401 }), reason: /sign in again/i },
    { name: '403', handler: () => json(403, { title: 'Forbidden', status: 403 }), reason: /not allowed to apply/i },
    { name: '500', handler: () => json(500, { title: 'Internal Server Error', status: 500 }), reason: /try again/i },
    { name: 'a network failure', handler: () => 'abort', reason: /try again/i },
  ];
  for (const { name, handler, reason } of failures) {
    test(`[MAM-64 AC-6] ${name}: the summary "The application could not be submitted" gives the reason, the typed values stay and the user can submit again`, async ({ page }) => {
      const api = await openApply(page, { 'POST /api/v1/applications': handler });
      await fillIn(page, RIVERA);

      await submit(page);

      await expect(summaryHeading(page, 'The application could not be submitted')).toBeVisible();
      await expect(page.getByRole('main').getByText(reason).first()).toBeVisible();
      await expect(page).toHaveURL(/\/portal\/apply$/);
      for (const [fieldName, value] of Object.entries(RIVERA) as Array<[Field, string]>) await expect(field(page, fieldName)).toHaveValue(value);
      await expect(page.getByRole('main').getByText(/Validated|Rejected/)).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Submit application' })).toBeEnabled();
      await submit(page);
      await expect.poll(() => api.to('POST', '/api/v1/applications').length).toBe(2);
    });
  }
});

test.describe('[MAM-64 AC-7] the form has zero axe violations in every state', () => {
  test('[MAM-64 AC-7] the empty form has zero axe violations', async ({ page }) => {
    await openApply(page, {});

    await expectNoAxeViolations(page);
  });

  test('[MAM-64 AC-7] the form for a group has zero axe violations', async ({ page }) => {
    await openApply(page, {});
    await field(page, 'providerType').selectOption('GR');
    await expect(field(page, 'legalName')).toBeVisible();

    await expectNoAxeViolations(page);
  });

  test('[MAM-64 AC-7] the form after the one-error rejection (E104) has zero axe violations', async ({ page }) => {
    await openApply(page, { 'POST /api/v1/applications': () => rejection('E104') });
    await fillIn(page, { ...RIVERA, taxonomy: '' });
    await submit(page);
    await expect(summaryHeading(page, '1 error in the application')).toBeVisible();

    await expectNoAxeViolations(page);
  });

  test('[MAM-64 AC-7] the form after the two-error rejection (E102 and E104) has zero axe violations', async ({ page }) => {
    await openApply(page, { 'POST /api/v1/applications': () => rejection('E102', 'E104') });
    await fillIn(page, { ...RIVERA, providerType: '', taxonomy: '' });
    await submit(page);
    await expect(summaryHeading(page, '2 errors in the application')).toBeVisible();

    await expectNoAxeViolations(page);
  });

  test('[MAM-64 AC-7] the form after a failed call (500) has zero axe violations', async ({ page }) => {
    await openApply(page, { 'POST /api/v1/applications': () => json(500, { title: 'Internal Server Error', status: 500 }) });
    await fillIn(page, RIVERA);
    await submit(page);
    await expect(summaryHeading(page, 'The application could not be submitted')).toBeVisible();

    await expectNoAxeViolations(page);
  });

  test('[MAM-64 AC-7] every input of the form has a visible label', async ({ page }) => {
    await openApply(page, {});

    const withoutVisibleLabel = await page.locator('main form').locator('input, select, textarea').evaluateAll((elements) =>
      elements
        .filter((element) => {
          const labels = Array.from((element as HTMLInputElement).labels ?? []);
          return !labels.some((label) => (label.textContent ?? '').trim() !== '' && label.getBoundingClientRect().width > 0 && getComputedStyle(label).visibility !== 'hidden');
        })
        .map((element) => element.id || element.getAttribute('name') || element.tagName),
    );
    expect(withoutVisibleLabel).toEqual([]);
  });

  test('[MAM-64 AC-7] after a failed submit the focus moves to the error summary; each error is code plus text next to its field', async ({ page }) => {
    await openApply(page, { 'POST /api/v1/applications': () => rejection('E102', 'E104') });
    await fillIn(page, { ...RIVERA, providerType: '', taxonomy: '' });

    await submit(page);

    await expect(summaryHeading(page, '2 errors in the application')).toBeVisible();
    await expect.poll(() => focusIsInSummary(page, '2 errors in the application')).toBe(true);
    await expect(field(page, 'providerType')).toHaveAccessibleDescription(described('E102'));
    await expect(field(page, 'taxonomy')).toHaveAccessibleDescription(described('E104'));
  });

  test('[MAM-64 AC-7] the whole form is completed and sent with Tab, Space and Enter only', async ({ page }) => {
    let created = false;
    const api = await openApply(page, {
      'POST /api/v1/applications': () => {
        created = true;
        return json(201, VALIDATED);
      },
      'GET /api/v1/applications': () => json(200, page_of(created ? [VALIDATED] : [])),
      [BY_ID]: () => (created ? json(200, VALIDATED) : json(404, { title: 'Not Found', status: 404 })),
    });

    await tabTo(page, field(page, 'npi'));
    expect(await focusIsVisible(page)).toBe(true);
    await page.keyboard.type(RIVERA.npi);
    await page.keyboard.press('Tab');
    await expect(field(page, 'medicaidId')).toBeFocused();
    await page.keyboard.type(RIVERA.medicaidId);
    await page.keyboard.press('Tab');
    await expect(field(page, 'providerType')).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(field(page, 'providerType')).toHaveValue('IN');
    await page.keyboard.press('Tab');
    await expect(field(page, 'taxonomy')).toBeFocused();
    await page.keyboard.type(RIVERA.taxonomy);
    for (const name of ['lastName', 'firstName', 'licenseNo', 'licenseState'] as const) {
      await page.keyboard.press('Tab');
      await expect(field(page, name)).toBeFocused();
      await page.keyboard.type(RIVERA[name]);
    }
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Submit application' })).toBeFocused();
    expect(await focusIsVisible(page)).toBe(true);
    await page.keyboard.press('Space');

    await expect(page).toHaveURL(new RegExp(`/portal/applications/${APPLICATION_ID}$`));
    await expect(applicationRegion(page).getByText('Validated')).toBeVisible();
    expect(api.to('POST', '/api/v1/applications')[0]?.body).toEqual({
      npi: NPI,
      medicaidId: 'TX0000111',
      providerType: 'IN',
      taxonomy: '207Q00000X',
      individual: { lastName: 'RIVERA', firstName: 'MARIA', licenseNo: 'MD12345', licenseState: 'TX' },
    });
  });

  test('[MAM-64 AC-7] Enter in an input sends the form; after the rejection Tab reaches the summary link and Enter moves the focus to the field', async ({ page }) => {
    await openApply(page, { 'POST /api/v1/applications': () => rejection('E104') });
    await fillIn(page, { ...RIVERA, taxonomy: '' });

    await field(page, 'licenseState').focus();
    await page.keyboard.press('Enter');

    await expect(summaryHeading(page, '1 error in the application')).toBeVisible();
    await expect.poll(() => focusIsInSummary(page, '1 error in the application')).toBe(true);
    await page.keyboard.press('Tab');
    await expect(summaryLinks(page).first()).toBeFocused();
    expect(await focusIsVisible(page)).toBe(true);
    await page.keyboard.press('Enter');
    await expect(field(page, 'taxonomy')).toBeFocused();
    expect(await focusIsVisible(page)).toBe(true);
  });
});

// sequence() lets a journey script successive answers (for example a rejection, then an acceptance).
test('[MAM-64 AC-4] after a rejection the corrected application is accepted and the old errors are gone', async ({ page }) => {
  let created = false;
  const post = sequence(rejection('E104'), json(201, VALIDATED));
  await openApply(page, {
    'POST /api/v1/applications': (call) => {
      const answer = post(call);
      if (answer !== 'abort' && answer.status === 201) created = true;
      return answer;
    },
    'GET /api/v1/applications': () => json(200, page_of(created ? [VALIDATED] : [])),
    [BY_ID]: () => (created ? json(200, VALIDATED) : json(404, { title: 'Not Found', status: 404 })),
  });
  await fillIn(page, { ...RIVERA, taxonomy: '' });
  await submit(page);
  await expect(summaryHeading(page, '1 error in the application')).toBeVisible();

  await field(page, 'taxonomy').fill(RIVERA.taxonomy);
  await submit(page);

  await expect(page).toHaveURL(new RegExp(`/portal/applications/${APPLICATION_ID}$`));
  await expect(applicationRegion(page).getByText('Validated')).toBeVisible();
  await expect(page.getByText(/\bE104:/)).toHaveCount(0);
});
