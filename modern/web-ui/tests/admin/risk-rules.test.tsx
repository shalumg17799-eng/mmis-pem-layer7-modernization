// MAM-92 AC-1..AC-6 (component level): the admin page /console/admin/risk-rules shows the data-driven taxonomy-prefix table of the
// risk tier (legacy values at version 1), lets an administrator add, change and remove rows and the default tier, and saves the
// result as a new version with PUT /api/v1/risk-rules (Q-005, pem-domain section 3). Invalid prefixes are refused at the cell.
import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { problem, response } from '../support/fakeApi';
import { api, installApi, renderApp, setupShellWorld, signedInAs, text } from '../support/world';
import {
  addButton,
  defaultTierList,
  describedText,
  findVersionHeading,
  LEGACY_RULES,
  openRules,
  prefixBox,
  riskWorld,
  RULES_ROUTE,
  ruleSet,
  SAVE_ROUTE,
  saveButton,
  savedBody,
  saves,
  setPrefix,
  tableRows,
  tierList,
} from '../support/risk';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const BAD_PREFIX = 'A prefix is exactly 3 upper-case letters or digits';

describe('[MAM-92 AC-1] the current table is shown with the legacy values', () => {
  it('[MAM-92 AC-1] the page calls GET /api/v1/risk-rules and shows "Version 1 (default tier: Limited (L))"', async () => {
    await openRules(riskWorld().routes);

    expect(text(await findVersionHeading())).toBe('Version 1 (default tier: Limited (L))');
    expect(api.to('GET', '/api/v1/risk-rules').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
  });

  it('[MAM-92 AC-1] the table Taxonomy prefix | Tier shows 332 High (H), 251 High (H), 341 Moderate (M), 291 Moderate (M) and 225 Moderate (M)', async () => {
    await openRules(riskWorld().routes);
    await findVersionHeading();

    const table = screen.getAllByRole('table').find((candidate) => within(candidate).queryByRole('columnheader', { name: 'Taxonomy prefix' }))!;
    expect(within(table).getAllByRole('columnheader').map((header) => text(header)).slice(0, 2)).toEqual(['Taxonomy prefix', 'Tier']);
    expect(tableRows()).toEqual([
      ['332', 'High (H)'],
      ['251', 'High (H)'],
      ['341', 'Moderate (M)'],
      ['291', 'Moderate (M)'],
      ['225', 'Moderate (M)'],
    ]);
  });

  it('[MAM-92 AC-1] no meaning (a specialty name) is shown next to a prefix', async () => {
    await openRules(riskWorld().routes);
    await findVersionHeading();

    const rows = Array.from(screen.getByRole('main').querySelectorAll('tbody tr'));
    for (const row of rows) expect(text(row).replace(/\b(High|Moderate|Limited) \([HML]\)|Remove row \d+ \(\w*\)|\d{3}/g, '').trim()).toBe('');
  });

  it('[MAM-92 AC-1] the default tier is a list of High (H), Moderate (M) and Limited (L), set to Limited (L)', async () => {
    await openRules(riskWorld().routes);
    await findVersionHeading();

    expect(Array.from(defaultTierList().options).map((option) => option.textContent)).toEqual(['High (H)', 'Moderate (M)', 'Limited (L)']);
    expect(defaultTierList()).toHaveValue('L');
  });

  it('[MAM-92 AC-1] a table that cannot be loaded says so with Retry and shows no table', async () => {
    let up = false;
    const { user } = await openRules(riskWorld({ [RULES_ROUTE]: () => (up ? response(200, ruleSet()) : response(503, problem(503, 'x'))) }).routes);
    expect(await screen.findByText('The risk rules could not be loaded. Try again.')).toBeInTheDocument();
    expect(tableRows()).toEqual([]);
    up = true;

    await user.click(screen.getByRole('button', { name: 'Retry' }));

    await findVersionHeading();
    expect(tableRows()).toHaveLength(5);
  });
});

describe('[MAM-92 AC-2] a changed table is saved as a new version', () => {
  it('[MAM-92 AC-2] adding the row 207 Moderate (M) and "Save as new version" send PUT /api/v1/risk-rules with the loaded version 1, defaultTier L and the six rules', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.click(addButton());
    await setPrefix(user, 6, '207');
    await user.selectOptions(tierList(6), 'M');
    await user.click(saveButton());

    await waitFor(() => expect(saves()).toHaveLength(1));
    expect(savedBody(saves()[0])).toEqual({ version: 1, defaultTier: 'L', rules: [...LEGACY_RULES, { taxonomyPrefix: '207', tier: 'M' }] });
    expect(saves()[0]?.headers.get('content-type')).toMatch(/application\/json/);
    expect(saves()[0]?.headers.get('authorization')).toMatch(/^Bearer \S+/);
  });

  it('[MAM-92 AC-2] after the 200 the page shows "Version 2" with the six rows returned by the API', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.click(addButton());
    await setPrefix(user, 6, '207');
    await user.selectOptions(tierList(6), 'M');
    await user.click(saveButton());

    await waitFor(async () => expect(text(await findVersionHeading())).toBe('Version 2 (default tier: Limited (L))'));
    expect(tableRows()).toEqual([
      ['332', 'High (H)'],
      ['251', 'High (H)'],
      ['341', 'Moderate (M)'],
      ['291', 'Moderate (M)'],
      ['225', 'Moderate (M)'],
      ['207', 'Moderate (M)'],
    ]);
    expect(screen.getByText(/Risk rules saved as version 2/)).toBeInTheDocument();
  });

  it('[MAM-92 AC-2] a new row starts empty with the tier Limited (L) and the focus on its prefix', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.click(addButton());

    expect(prefixBox(6)).toHaveValue('');
    expect(tierList(6)).toHaveValue('L');
    expect(prefixBox(6)).toHaveFocus();
  });
});

describe('[MAM-92 AC-3] rows and the default tier can be edited', () => {
  it('[MAM-92 AC-3] 225 changed to High (H), 291 removed and the default tier Moderate (M): the PUT body has exactly those rows and that default tier', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.selectOptions(tierList(5), 'H');
    await user.click(screen.getByRole('button', { name: 'Remove row 4 (291)' }));
    await user.selectOptions(defaultTierList(), 'M');
    await user.click(saveButton());

    await waitFor(() => expect(saves()).toHaveLength(1));
    expect(savedBody(saves()[0])).toEqual({
      version: 1,
      defaultTier: 'M',
      rules: [
        { taxonomyPrefix: '332', tier: 'H' },
        { taxonomyPrefix: '251', tier: 'H' },
        { taxonomyPrefix: '341', tier: 'M' },
        { taxonomyPrefix: '225', tier: 'H' },
      ],
    });
  });

  it('[MAM-92 AC-3] the table shown after saving matches what was sent', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.selectOptions(tierList(5), 'H');
    await user.click(screen.getByRole('button', { name: 'Remove row 4 (291)' }));
    await user.selectOptions(defaultTierList(), 'M');
    await user.click(saveButton());

    await waitFor(async () => expect(text(await findVersionHeading())).toBe('Version 2 (default tier: Moderate (M))'));
    expect(tableRows()).toEqual([
      ['332', 'High (H)'],
      ['251', 'High (H)'],
      ['341', 'Moderate (M)'],
      ['225', 'High (H)'],
    ]);
    expect(defaultTierList()).toHaveValue('M');
  });

  it('[MAM-92 AC-3] the tiers are chosen from High (H), Moderate (M) and Limited (L) only', async () => {
    await openRules(riskWorld().routes);
    await findVersionHeading();

    for (const row of [1, 2, 3, 4, 5]) {
      expect(Array.from(tierList(row).options).map((option) => option.textContent)).toEqual(['High (H)', 'Moderate (M)', 'Limited (L)']);
    }
  });

  it('[MAM-92 AC-3] the Remove buttons name the row they act on, so removing one row leaves the others', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.click(screen.getByRole('button', { name: 'Remove row 2 (251)' }));

    expect(tableRows().map((row) => row[0])).toEqual(['332', '341', '291', '225']);
  });
});

describe('[MAM-92 AC-4] invalid prefixes are refused at the field', () => {
  it.each([
    ['20', '20'],
    ['2o7', '2o7'],
    ['an empty prefix', ''],
  ])('[MAM-92 AC-4] %s: nothing is sent; the error summary links to the cell and the message is next to it', async (_label, value) => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.click(addButton());
    await setPrefix(user, 6, value);
    await user.click(saveButton());

    const heading = await screen.findByRole('heading', { level: 2, name: '1 error in the risk rules' });
    expect(saves()).toHaveLength(0);
    expect(describedText(prefixBox(6))).toContain(BAD_PREFIX);
    expect(prefixBox(6)).toHaveAttribute('aria-invalid', 'true');
    await waitFor(() => expect(document.activeElement?.contains(heading)).toBe(true));
    await user.click(screen.getByRole('link', { name: `Row 6: ${BAD_PREFIX}` }));
    expect(prefixBox(6)).toHaveFocus();
    expect(prefixBox(6)).toHaveValue(value);
  });

  it('[MAM-92 AC-4] two invalid cells give "2 errors in the risk rules", each linked and each with its message', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await setPrefix(user, 1, '20');
    await setPrefix(user, 3, '2o7');
    await user.click(saveButton());

    expect(await screen.findByRole('heading', { level: 2, name: '2 errors in the risk rules' })).toBeInTheDocument();
    expect(describedText(prefixBox(1))).toContain(BAD_PREFIX);
    expect(describedText(prefixBox(3))).toContain(BAD_PREFIX);
    expect(screen.getByRole('link', { name: `Row 1: ${BAD_PREFIX}` })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: `Row 3: ${BAD_PREFIX}` })).toBeInTheDocument();
    expect(saves()).toHaveLength(0);
  });

  it('[MAM-92 AC-4] a prefix of three upper-case letters or digits (A1B) is accepted and sent', async () => {
    const { user } = await openRules(riskWorld().routes);
    await findVersionHeading();

    await user.click(addButton());
    await setPrefix(user, 6, 'A1B');
    await user.click(saveButton());

    await waitFor(() => expect(saves()).toHaveLength(1));
    expect(savedBody(saves()[0]).rules.at(-1)).toEqual({ taxonomyPrefix: 'A1B', tier: 'L' });
  });
});

describe('[MAM-92 AC-5] refusals from the service are shown and the table is reloaded', () => {
  async function refused(status: 400 | 422, detail: string) {
    // another administrator saved version 2 (a different table) while this page edited version 1
    const world = riskWorld({}, ruleSet());
    const other = ruleSet({ version: 2, defaultTier: 'M', rules: [{ taxonomyPrefix: '111', tier: 'H' }] });
    let held = ruleSet();
    world.routes[RULES_ROUTE] = () => response(200, held);
    world.routes[SAVE_ROUTE] = () => {
      held = other;
      return response(status, { title: 'refused', status, detail });
    };
    const rendered = await openRules(world.routes);
    await findVersionHeading();
    await rendered.user.click(addButton());
    await setPrefix(rendered.user, 6, '207');
    await rendered.user.click(saveButton());
    return rendered;
  }

  it.each([
    [422, 'The table was changed by someone else: you edited version 1, the service holds version 2 (message of this test)'],
    [422, 'The prefix 207 is listed twice (message of this test)'],
    [400, 'The rules are malformed (message of this test)'],
  ] as const)('[MAM-92 AC-5] a %s shows the problem detail "%s"', async (status, detail) => {
    await refused(status, detail);

    expect(await screen.findByText(detail)).toBeInTheDocument();
    expect(screen.queryByText(/Risk rules saved/)).toBeNull();
  });

  it('[MAM-92 AC-5] the page reloads and shows the table that the service holds now, with its version; the rejected table is not shown as saved', async () => {
    await refused(422, 'The table was changed by someone else (message of this test)');

    await waitFor(async () => expect(text(await findVersionHeading())).toBe('Version 2 (default tier: Moderate (M))'));
    expect(tableRows()).toEqual([['111', 'High (H)']]);
    expect(screen.queryByDisplayValue('207')).toBeNull();
    expect(defaultTierList()).toHaveValue('M');
  });
});

describe('[MAM-92 AC-6] only administrators open the screen', () => {
  it('[MAM-92 AC-6] admin1 sees the table, the Save button and its menu entry "Risk-tier rules"', async () => {
    await openRules(riskWorld().routes);
    await findVersionHeading();

    expect(saveButton()).toBeEnabled();
    expect(within(screen.getAllByRole('navigation')[0]!).getByRole('link', { name: 'Risk-tier rules' })).toHaveAttribute('href', '/console/admin/risk-rules');
  });

  it.each(['supervisor1', 'analyst1', 'provider1'] as const)('[MAM-92 AC-6] %s has no menu entry, sees the access-denied page when typing the address, and no risk-rules request is made', async (who) => {
    signedInAs(who);
    installApi(riskWorld().routes);
    renderApp('/console/admin/risk-rules');

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Risk-tier rules' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Save as new version' })).toBeNull();
    expect(api.calls.filter((call) => call.url.pathname === '/api/v1/risk-rules')).toHaveLength(0);
  });

  it('[MAM-92 AC-6] a 403 to a save shows "You are not allowed to change the risk rules" and the table is not shown as saved', async () => {
    const { user } = await openRules(riskWorld({ [SAVE_ROUTE]: () => response(403, problem(403, 'Forbidden')) }).routes);
    await findVersionHeading();

    await user.click(addButton());
    await setPrefix(user, 6, '207');
    await user.click(saveButton());

    expect(await screen.findByText('You are not allowed to change the risk rules')).toBeInTheDocument();
    expect(screen.queryByText(/Risk rules saved/)).toBeNull();
    expect(text(await findVersionHeading())).toBe('Version 1 (default tier: Limited (L))');
  });

  it('[MAM-92 AC-6] a failed save (5xx) says so and keeps what was edited', async () => {
    const { user } = await openRules(riskWorld({ [SAVE_ROUTE]: () => response(503, problem(503, 'x')) }).routes);
    await findVersionHeading();

    await user.click(addButton());
    await setPrefix(user, 6, '207');
    await user.click(saveButton());

    expect(await screen.findByText('The risk rules could not be saved. Try again.')).toBeInTheDocument();
    expect(prefixBox(6)).toHaveValue('207');
  });
});
