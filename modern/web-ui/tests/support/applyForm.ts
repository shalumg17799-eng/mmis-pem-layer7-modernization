// MAM-64 component-test helpers (a new support file: the MAM-62 support files are recorded and stay untouched).
// They drive the apply form of /portal/apply through the rendered App and check the error summary the way a
// screen-reader user meets it: code plus message, once in the summary and once at the field.
import { screen, within } from '@testing-library/react';
import type { UserEvent } from '@testing-library/user-event';
import { expect } from 'vitest';
import type { components as EnrollmentSchemas } from '../../src/shared/api/generated/enrollment-service';
import { response, type ApiCall, type Handler } from './fakeApi';
import { api, defaultRoutes, installApi, renderApp, signedInAs, text, type Rendered } from './world';

export type FieldError = EnrollmentSchemas['schemas']['FieldError'];

export type FieldName =
  | 'npi'
  | 'medicaidId'
  | 'providerType'
  | 'taxonomy'
  | 'lastName'
  | 'firstName'
  | 'licenseNo'
  | 'licenseState'
  | 'legalName'
  | 'dbaName'
  | 'ownerNpi';

/** The visible label of each field (MAM-64 AC-1; wireframe portal-apply.html). Matched at the start of the label text. */
export const LABEL: Record<FieldName, RegExp> = {
  npi: /^NPI\b/,
  medicaidId: /^Medicaid ID\b/,
  providerType: /^Provider type\b/,
  taxonomy: /^Taxonomy code\b/,
  lastName: /^Last name\b/,
  firstName: /^First name\b/,
  licenseNo: /^Licence number\b/,
  licenseState: /^Licence state\b/,
  legalName: /^Legal name\b/,
  dbaName: /^DBA name\b/,
  ownerNpi: /^Owner NPI\b/,
};

/** Application of AC-2: the synthetic provider RIVERA MARIA (aws-transform/test-data/providers.txt). */
export const RIVERA = {
  npi: '1003000111',
  medicaidId: 'TX0000111',
  providerType: 'IN',
  taxonomy: '207Q00000X',
  lastName: 'RIVERA',
  firstName: 'MARIA',
  licenseNo: 'MD12345',
  licenseState: 'TX',
} as const;

export const SUBMIT_ROUTE = 'POST /api/v1/applications';
export const LIST_ROUTE = 'GET /api/v1/applications';

export function submissions(): ApiCall[] {
  return api.to('POST', '/api/v1/applications');
}

/** The JSON body of a recorded POST. */
export function sentBody(call: ApiCall | undefined): Record<string, unknown> {
  if (!call?.body) throw new Error('no POST /api/v1/applications with a body was sent');
  return JSON.parse(call.body) as Record<string, unknown>;
}

export function control(name: FieldName): HTMLInputElement | HTMLSelectElement {
  return within(screen.getByRole('main')).getByLabelText(LABEL[name]);
}

export function queryControl(name: FieldName): HTMLElement | null {
  return within(screen.getByRole('main')).queryByLabelText(LABEL[name]);
}

/** Texts of the label(s) of a control. */
export function labelText(element: HTMLElement): string {
  return Array.from((element as HTMLInputElement).labels ?? [])
    .map((label) => text(label))
    .join(' ');
}

/** Texts of the elements named by aria-describedby (hints and error messages). */
export function describedText(element: HTMLElement): string {
  return (element.getAttribute('aria-describedby') ?? '')
    .split(/\s+/)
    .filter(Boolean)
    .map((id) => text(document.getElementById(id)))
    .join(' ');
}

/** Open /portal/apply as provider1; `routes` replace the default gateway answers. */
export async function openApplyForm(routes: Record<string, Handler> = {}): Promise<Rendered> {
  signedInAs('provider1');
  installApi({ ...defaultRoutes(), ...routes });
  const rendered = renderApp('/portal/apply');
  await screen.findByRole('button', { name: 'Submit application' });
  return rendered;
}

/** Fill the given fields the way a person does: choose the type first (it decides which inputs exist), then type. */
export async function fillIn(user: UserEvent, values: Partial<Record<FieldName, string>>): Promise<void> {
  const order: FieldName[] = ['providerType', 'npi', 'medicaidId', 'taxonomy', 'lastName', 'firstName', 'licenseNo', 'licenseState', 'legalName', 'dbaName', 'ownerNpi'];
  for (const name of order) {
    const value = values[name];
    if (value === undefined) continue;
    const element = control(name);
    if (name === 'providerType') {
      await user.selectOptions(element, value);
    } else {
      await user.clear(element);
      if (value !== '') await user.type(element, value);
    }
  }
}

export async function submit(user: UserEvent): Promise<void> {
  await user.click(screen.getByRole('button', { name: 'Submit application' }));
}

export function rivera(overrides: Partial<Record<FieldName, string>> = {}): Partial<Record<FieldName, string>> {
  return { ...RIVERA, ...overrides };
}

// ---- answers of the enrollment service --------------------------------------------------------------------------

export type ErrorCode = 'E101' | 'E102' | 'E103' | 'E104';

export const FIELD_OF: Record<ErrorCode, FieldName> = { E101: 'npi', E102: 'providerType', E103: 'npi', E104: 'taxonomy' };

/** The messages enrollment-service sends today (ErrorCode.java); the page must show whatever message it is given. */
export const MESSAGE_OF: Record<ErrorCode, string> = {
  E101: 'Enter the 10-digit NPI.',
  E102: 'The provider type must be IN, GR or FA.',
  E103: 'An application for this NPI has already been accepted.',
  E104: 'Enter the taxonomy code.',
};

export function fieldError(code: ErrorCode, message: string = MESSAGE_OF[code]): FieldError {
  return { code, field: FIELD_OF[code], message };
}

/** 422 problem+json of a rejected application: every failed check once (D-001); `code` only when there is exactly one. */
export function rejection(...errors: FieldError[]): Handler {
  const only = errors.length === 1 ? errors[0] : undefined;
  return () =>
    response(422, {
      title: 'Unprocessable Entity',
      status: 422,
      detail: 'The application failed validation.',
      ...(only ? { code: only.code } : {}),
      errors,
    });
}

// ---- checks on the error summary --------------------------------------------------------------------------------

/** The links of the error summary: "E104: <message>". They are the only links of the page that start with an error code. */
export function summaryLinks(): HTMLElement[] {
  return within(screen.getByRole('main')).queryAllByRole('link', { name: /^E\d{3}: / });
}

/** How often a code with its colon ("E104:") is written on the page: in the summary links and elsewhere (at the fields). */
export function occurrences(code: ErrorCode): { inSummary: number; atFields: number } {
  const found = within(screen.getByRole('main')).queryAllByText(new RegExp(`\\b${code}:`));
  return { inSummary: found.filter((element) => element.closest('a')).length, atFields: found.filter((element) => !element.closest('a')).length };
}

/** The error is written next to the field (named by aria-describedby) as "<code>: <message>" and the field is marked invalid. */
export function expectErrorAt(name: FieldName, error: FieldError): void {
  const element = control(name);
  expect(element).toHaveAttribute('aria-invalid', 'true');
  expect(describedText(element)).toContain(`${error.code}: ${error.message}`);
}
