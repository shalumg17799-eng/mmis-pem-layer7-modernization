import { ApiError } from '../../../shared/api/client';
import type { ApplicationRequest, FieldError } from '../../../shared/api/types';
import { PROVIDER_TYPE_LABEL, withCode } from '../../../shared/format/format';

/** The inputs of the application form. The names are the field names of the enrollment contract (FieldError.field). */
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

export type FormValues = Record<FieldName, string>;

export const EMPTY_VALUES: FormValues = {
  npi: '',
  medicaidId: '',
  providerType: '',
  taxonomy: '',
  lastName: '',
  firstName: '',
  licenseNo: '',
  licenseState: '',
  legalName: '',
  dbaName: '',
  ownerNpi: '',
};

export interface TextFieldSpec {
  name: Exclude<FieldName, 'providerType'>;
  label: string;
  hint?: string;
  /** Maximum length of the contract (ApplicationRequest, Individual, Organization); the legacy fields are fixed-width. */
  maxLength: number;
  numeric?: boolean;
}

export const NPI_FIELD: TextFieldSpec = {
  name: 'npi',
  label: 'NPI (10 digits)',
  hint: 'Your National Provider Identifier: 10 digits, for example 1003000111.',
  maxLength: 10,
  numeric: true,
};
export const MEDICAID_ID_FIELD: TextFieldSpec = {
  name: 'medicaidId',
  label: 'Medicaid ID',
  hint: 'The ID the state issued to you: at most 9 characters, for example TX0000111.',
  maxLength: 9,
};
export const TAXONOMY_FIELD: TextFieldSpec = {
  name: 'taxonomy',
  label: 'Taxonomy code',
  hint: 'The code of your specialty: 10 characters, for example 207Q00000X.',
  maxLength: 10,
};

/** What the Provider type list means, shown under it. */
export const PROVIDER_TYPE_HINT = 'Choose Individual for a person, Group or Facility for an organisation; the fields below follow your choice.';

export const INDIVIDUAL_FIELDS: readonly TextFieldSpec[] = [
  { name: 'lastName', label: 'Last name', hint: 'As on your licence, at most 30 characters, for example RIVERA.', maxLength: 30 },
  { name: 'firstName', label: 'First name', hint: 'As on your licence, at most 20 characters, for example MARIA.', maxLength: 20 },
  { name: 'licenseNo', label: 'Licence number', hint: 'Your state licence number, at most 15 characters, for example MD12345.', maxLength: 15 },
  { name: 'licenseState', label: 'Licence state', hint: 'The 2-letter code of the state that issued it, for example TX.', maxLength: 2 },
];

export const ORGANISATION_FIELDS: readonly TextFieldSpec[] = [
  { name: 'legalName', label: 'Legal name', hint: 'The registered name of the organisation, at most 60 characters.', maxLength: 60 },
  { name: 'dbaName', label: 'DBA name', hint: 'The trade name ("doing business as"), at most 30 characters; leave it empty if it is the same.', maxLength: 30 },
  { name: 'ownerNpi', label: 'Owner NPI', hint: 'The 10-digit NPI of the owner, for example 1003000111.', maxLength: 10, numeric: true },
];

/** The type list: Individual (IN), Group (GR), Facility (FA) (D-008). */
export const PROVIDER_TYPE_OPTIONS = (Object.keys(PROVIDER_TYPE_LABEL) as Array<keyof typeof PROVIDER_TYPE_LABEL>).map((code) => ({
  value: code,
  label: withCode(code, PROVIDER_TYPE_LABEL),
}));

export function isOrganisation(providerType: string): boolean {
  return providerType === 'GR' || providerType === 'FA';
}

/** The inputs of the type-specific part: the organisation inputs for Group and Facility, otherwise the individual inputs. */
export function variantFields(providerType: string): readonly TextFieldSpec[] {
  return isOrganisation(providerType) ? ORGANISATION_FIELDS : INDIVIDUAL_FIELDS;
}

/** Every input that is on the screen for this type, in screen order. */
export function shownFields(providerType: string): FieldName[] {
  return ['npi', 'medicaidId', 'providerType', 'taxonomy', ...variantFields(providerType).map((spec) => spec.name)];
}

function present<K extends FieldName>(values: FormValues, names: readonly K[]): Partial<Record<K, string>> | undefined {
  const picked: Partial<Record<K, string>> = {};
  let any = false;
  for (const name of names) {
    const value = values[name].trim();
    if (value !== '') {
      picked[name] = value;
      any = true;
    }
  }
  return any ? picked : undefined;
}

/**
 * The body of POST /api/v1/applications. The four required members are always sent, even when empty, so that the service
 * answers 422 with E101-E104 instead of 400 (D-001); optional members that were left empty are left out.
 */
export function toRequest(values: FormValues): ApplicationRequest {
  const request: ApplicationRequest = {
    npi: values.npi.trim(),
    medicaidId: values.medicaidId.trim(),
    providerType: values.providerType,
    taxonomy: values.taxonomy.trim(),
  };
  if (values.providerType === 'IN') {
    const individual = present(values, ['lastName', 'firstName', 'licenseNo', 'licenseState']);
    if (individual) request.individual = individual;
  } else if (isOrganisation(values.providerType)) {
    const organization = present(values, ['legalName', 'dbaName', 'ownerNpi']);
    if (organization) request.organization = organization;
  }
  return request;
}

/** "E104: <message of the service>": the code is always written in full, followed by the message the API returned. */
export function errorText(error: FieldError): string {
  return error.message ? `${error.code}: ${error.message}` : error.code;
}

export interface FailureView {
  reason: string;
  /** A 401: the page offers to sign in again. */
  signInAgain: boolean;
}

/** What the page says for a failure that is not a business rejection (AC-6): never swallowed, never a claim of success. */
export function describeFailure(error: unknown): FailureView {
  if (!(error instanceof ApiError)) {
    return { reason: 'The application could not be sent. Check your connection and try again.', signInAgain: false };
  }
  switch (error.status) {
    case 401:
      return { reason: 'Your session has ended. Sign in again, then submit the application.', signInAgain: true };
    case 403:
      return { reason: 'You are not allowed to apply for enrolment with this account.', signInAgain: false };
    case 400:
      return { reason: `${error.problem?.detail ?? 'The request is not valid.'} Check the form and try again.`, signInAgain: false };
    default:
      return {
        reason:
          error.status >= 500 || !error.problem?.detail
            ? 'The service could not process the application. Try again.'
            : `${error.problem.detail} Try again.`,
        signInAgain: false,
      };
  }
}

/** A value for the review: what was entered, or that nothing was yet. */
function shown(value: string): string {
  return value.trim() === '' ? 'Not entered yet' : value.trim();
}

/** The rows of the live "Review your answers" summary, in the order of the form. Read-only; nothing here is sent. */
export function reviewRows(values: FormValues): Array<[string, string]> {
  const type = values.providerType === '' ? 'Not chosen yet' : withCode(values.providerType, PROVIDER_TYPE_LABEL);
  const rows: Array<[string, string]> = [
    ['NPI', shown(values.npi)],
    ['Medicaid ID', shown(values.medicaidId)],
    ['Provider type', type],
    ['Taxonomy code', shown(values.taxonomy)],
  ];
  for (const spec of variantFields(values.providerType)) rows.push([spec.label, shown(values[spec.name])]);
  return rows;
}
