// MAM-64 AC-1: /portal/apply shows every field of the application with a visible label (wireframe portal-apply.html,
// contract ApplicationRequest / Individual / Organization, frontend-stack section 5).
import { screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { control, fillIn, labelText, LABEL, openApplyForm, queryControl, describedText, submissions, type FieldName } from '../support/applyForm';
import { setupShellWorld, text } from '../support/world';

vi.mock('oidc-client-ts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('oidc-client-ts')>();
  const { FakeUserManager } = await import('../support/fakeOidc');
  return { ...actual, UserManager: FakeUserManager };
});

setupShellWorld();

const INDIVIDUAL_FIELDS: FieldName[] = ['lastName', 'firstName', 'licenseNo', 'licenseState'];
const ORGANISATION_FIELDS: FieldName[] = ['legalName', 'dbaName', 'ownerNpi'];

// [field, label as written, element, maximum length (contract) or undefined for the list]
const COMMON: Array<[FieldName, string, 'INPUT' | 'SELECT', number | undefined]> = [
  ['npi', 'NPI (10 digits)', 'INPUT', 10],
  ['medicaidId', 'Medicaid ID', 'INPUT', 9],
  ['providerType', 'Provider type', 'SELECT', undefined],
  ['taxonomy', 'Taxonomy code', 'INPUT', 10],
];
const INDIVIDUAL: Array<[FieldName, string, 'INPUT', number]> = [
  ['lastName', 'Last name', 'INPUT', 30],
  ['firstName', 'First name', 'INPUT', 20],
  ['licenseNo', 'Licence number', 'INPUT', 15],
  ['licenseState', 'Licence state', 'INPUT', 2],
];
const ORGANISATION: Array<[FieldName, string, 'INPUT', number]> = [
  ['legalName', 'Legal name', 'INPUT', 60],
  ['dbaName', 'DBA name', 'INPUT', 30],
  ['ownerNpi', 'Owner NPI', 'INPUT', 10],
];

describe('[MAM-64 AC-1] the form shows every field with a visible label', () => {
  it('[MAM-64 AC-1] /portal/apply has one h1 "Apply for enrolment" inside the main landmark', async () => {
    await openApplyForm();

    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(within(screen.getByRole('main')).getByRole('heading', { level: 1, name: 'Apply for enrolment' })).toBeInTheDocument();
  });

  it('[MAM-64 AC-1] opening the form sends nothing to the API', async () => {
    await openApplyForm();

    expect(submissions()).toHaveLength(0);
  });

  it.each([...COMMON, ...INDIVIDUAL])('[MAM-64 AC-1] %s is a %s field with its own visible label element', async (name, label, tag) => {
    await openApplyForm();

    const element = control(name);
    expect(element.tagName).toBe(tag);
    const labels = Array.from(element.labels ?? []);
    expect(labels).toHaveLength(1);
    expect(text(labels[0])).toMatch(LABEL[name]);
    expect(text(labels[0])).toContain(label);
    expect(labels[0]).not.toHaveClass('usa-sr-only'); // a visible label, not a screen-reader-only one
  });

  it.each([...COMMON, ...INDIVIDUAL].filter((entry) => entry[3] !== undefined))('[MAM-64 AC-1] %s accepts at most the contract length', async (name, _label, _tag, limit) => {
    await openApplyForm();

    expect(control(name)).toHaveAttribute('maxlength', String(limit));
  });

  it('[MAM-64 AC-1] the NPI label says 10 digits, the Medicaid ID says at most 9 characters, the taxonomy says 10 characters', async () => {
    await openApplyForm();

    const visible = (name: FieldName) => `${labelText(control(name))} ${describedText(control(name))}`;
    expect(visible('npi')).toMatch(/10 digits/i);
    expect(visible('medicaidId')).toMatch(/9 characters/i);
    expect(visible('taxonomy')).toMatch(/10 characters/i);
  });

  it('[MAM-64 AC-1] Provider type is a list that starts with "Choose..." and offers exactly Individual (IN), Group (GR) and Facility (FA)', async () => {
    await openApplyForm();

    const options = Array.from((control('providerType') as HTMLSelectElement).options);
    expect(options.map((option) => option.textContent)).toEqual(['Choose...', 'Individual (IN)', 'Group (GR)', 'Facility (FA)']);
    expect(options.map((option) => option.value)).toEqual(['', 'IN', 'GR', 'FA']);
    expect((control('providerType') as HTMLSelectElement).value).toBe('');
  });

  it('[MAM-64 AC-1] before a type is chosen the individual inputs are shown and the organisation inputs are not (wireframe portal-apply.html)', async () => {
    await openApplyForm();

    for (const name of INDIVIDUAL_FIELDS) expect(queryControl(name)).not.toBeNull();
    for (const name of ORGANISATION_FIELDS) expect(queryControl(name)).toBeNull();
  });

  it('[MAM-64 AC-1] the individual inputs are shown for Individual and the organisation inputs are not', async () => {
    const { user } = await openApplyForm();
    await fillIn(user, { providerType: 'IN' });

    for (const name of INDIVIDUAL_FIELDS) expect(queryControl(name)).not.toBeNull();
    for (const name of ORGANISATION_FIELDS) expect(queryControl(name)).toBeNull();
  });

  it.each(['GR', 'FA'])('[MAM-64 AC-1] choosing %s replaces the individual inputs by Legal name, DBA name and Owner NPI', async (type) => {
    const { user } = await openApplyForm();
    await fillIn(user, { providerType: type });

    for (const name of INDIVIDUAL_FIELDS) expect(queryControl(name)).toBeNull();
    for (const [name, label, tag, limit] of ORGANISATION) {
      const element = control(name);
      expect(element.tagName).toBe(tag);
      expect(labelText(element)).toContain(label);
      expect(element).toHaveAttribute('maxlength', String(limit));
    }
  });

  it('[MAM-64 AC-1] choosing Individual again brings the individual inputs back and removes the organisation inputs', async () => {
    const { user } = await openApplyForm();
    await fillIn(user, { providerType: 'GR' });
    await fillIn(user, { providerType: 'IN' });

    for (const name of INDIVIDUAL_FIELDS) expect(queryControl(name)).not.toBeNull();
    for (const name of ORGANISATION_FIELDS) expect(queryControl(name)).toBeNull();
  });

  it('[MAM-64 AC-1] a "Submit application" button ends the form: it is the last control, after every input', async () => {
    await openApplyForm();

    const form = control('npi').closest('form');
    expect(form).not.toBeNull();
    const controls = Array.from(form!.querySelectorAll('input, select, textarea, button'));
    const last = controls.at(-1);
    expect(last?.tagName).toBe('BUTTON');
    expect(last).toHaveAttribute('type', 'submit');
    expect(last).toHaveAccessibleName('Submit application');
    expect(controls.filter((element) => element.tagName === 'BUTTON')).toHaveLength(1);
  });
});
