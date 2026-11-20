import { FormGroup, Label, Select, TextInput } from '@trussworks/react-uswds';
import type { ChangeEvent } from 'react';
import type { FieldError } from '../../../shared/api/types';
import { errorText, PROVIDER_TYPE_HINT, PROVIDER_TYPE_OPTIONS, type FieldName, type TextFieldSpec } from './applicationForm';

/** Hint and error ids of a field: the input points at both with aria-describedby, so a screen reader reads them with the label. */
function ids(name: FieldName, hint: boolean, errors: readonly FieldError[]) {
  const hintId = hint ? `${name}-hint` : undefined;
  const errorIds = errors.map((_, index) => `${name}-error-${index}`);
  const describedBy = [hintId, ...errorIds].filter(Boolean).join(' ');
  return { hintId, errorIds, describedBy: describedBy === '' ? undefined : describedBy };
}

function Messages({ errorIds, errors }: { errorIds: string[]; errors: readonly FieldError[] }) {
  // The summary above the form is the one live region; these messages are read with the field, not announced again.
  return errors.map((error, index) => (
    <span key={errorIds[index]} id={errorIds[index]} className="usa-error-message">
      {errorText(error)}
    </span>
  ));
}

/** A neutral hint while typing a number of fixed length ("3 of 10 digits"): it never blocks, never says "error" (MAM-64 AC-3). */
function progress(spec: TextFieldSpec, value: string): string {
  if (!spec.numeric || value === '') return '';
  return ` You have typed ${value.length} of ${spec.maxLength} digits.`;
}

interface TextFieldProps {
  spec: TextFieldSpec;
  value: string;
  errors: readonly FieldError[];
  onChange: (name: FieldName, value: string) => void;
}

/** A labelled text input with its hint and the errors of the service for it (code and message, next to the field). */
export function TextField({ spec, value, errors, onChange }: TextFieldProps) {
  const { hintId, errorIds, describedBy } = ids(spec.name, spec.hint !== undefined, errors);
  const invalid = errors.length > 0;
  return (
    <FormGroup error={invalid}>
      <Label htmlFor={spec.name} error={invalid}>
        {spec.label}
      </Label>
      {spec.hint !== undefined && (
        <div className="usa-hint" id={hintId}>
          {spec.hint}
          {progress(spec, value)}
        </div>
      )}
      <Messages errorIds={errorIds} errors={errors} />
      <TextInput
        id={spec.name}
        name={spec.name}
        type="text"
        value={value}
        maxLength={spec.maxLength}
        {...(spec.numeric ? { inputMode: 'numeric' as const } : {})}
        validationStatus={invalid ? 'error' : undefined}
        aria-describedby={describedBy}
        aria-invalid={invalid ? true : undefined}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(spec.name, event.target.value)}
      />
    </FormGroup>
  );
}

/** Provider type: "Choose..." followed by Individual (IN), Group (GR) and Facility (FA). */
export function ProviderTypeField({ value, errors, onChange }: Omit<TextFieldProps, 'spec'>) {
  const { hintId, errorIds, describedBy } = ids('providerType', true, errors);
  const invalid = errors.length > 0;
  return (
    <FormGroup error={invalid}>
      <Label htmlFor="providerType" error={invalid}>
        Provider type
      </Label>
      <div className="usa-hint" id={hintId}>
        {PROVIDER_TYPE_HINT}
      </div>
      <Messages errorIds={errorIds} errors={errors} />
      <Select
        id="providerType"
        name="providerType"
        value={value}
        validationStatus={invalid ? 'error' : undefined}
        aria-describedby={describedBy}
        aria-invalid={invalid ? true : undefined}
        onChange={(event: ChangeEvent<HTMLSelectElement>) => onChange('providerType', event.target.value)}
      >
        <option value="">Choose...</option>
        {PROVIDER_TYPE_OPTIONS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </Select>
    </FormGroup>
  );
}
