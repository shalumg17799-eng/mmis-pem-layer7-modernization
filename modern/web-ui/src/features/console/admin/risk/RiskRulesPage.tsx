import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, AlertText, Button, FormGroup, Label, Select, Table, TextInput } from '@trussworks/react-uswds';
import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { ApiError } from '../../../../shared/api/client';
import { RISK_RULES, type RiskRule, type RiskRuleSet } from '../../../../shared/api/types';
import { useApi } from '../../../../shared/api/useApi';
import { useSession } from '../../../../shared/auth/AuthProvider';
import { RISK_TIER_LABEL, withCode } from '../../../../shared/format/format';
import { ErrorSummary } from '../../../../shared/ui/ErrorSummary';
import { PageHeading } from '../../../../shared/ui/PageHeading';

type Tier = RiskRule['tier'];
const TIERS: readonly Tier[] = ['H', 'M', 'L'];
/** A taxonomy prefix is exactly 3 upper-case letters or digits (contract RiskRule.taxonomyPrefix). */
const PREFIX = /^[0-9A-Z]{3}$/;
const BAD_PREFIX = 'A prefix is exactly 3 upper-case letters or digits';

interface Row {
  key: number;
  prefix: string;
  tier: Tier;
}

interface Draft {
  defaultTier: Tier;
  rows: Row[];
}

let nextKey = 0;
const toDraft = (set: RiskRuleSet): Draft => ({ defaultTier: set.defaultTier, rows: set.rules.map((rule) => ({ key: (nextKey += 1), prefix: rule.taxonomyPrefix, tier: rule.tier })) });

type Outcome = { kind: 'invalid'; rows: number[]; attempt: number } | { kind: 'refused'; heading: string; detail?: string; attempt: number };

function describeRefusal(error: unknown): { heading: string; detail?: string; reload: boolean } {
  if (error instanceof ApiError) {
    if (error.status === 403) return { heading: 'You are not allowed to change the risk rules', reload: false };
    if (error.status === 400 || error.status === 422) return { heading: 'The risk rules were not saved', detail: error.problem?.detail ?? 'The table was not accepted.', reload: true };
  }
  return { heading: 'The risk rules could not be saved. Try again.', reload: false };
}

/**
 * console.risk-rules (MAM-92): the data-driven taxonomy-prefix table of the risk tier (Q-005, legacy values at version 1). An
 * administrator edits the rows and the default tier and saves the table as a new version with the version that was loaded. The
 * page refuses a prefix that is not 3 upper-case letters or digits before it sends anything; a refusal of the service (a table
 * changed meanwhile, a prefix listed twice, a malformed table) is shown with its detail and the table is reloaded (D-024).
 */
export function RiskRulesPage() {
  const api = useApi();
  const user = useSession();
  const queryClient = useQueryClient();
  const key = ['risk-rules', user.subject];
  const query = useQuery({ queryKey: key, queryFn: ({ signal }) => api.get<RiskRuleSet>(RISK_RULES, signal) });
  const [draft, setDraft] = useState<Draft | null>(null);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [saved, setSaved] = useState<number | null>(null);
  const [focusKey, setFocusKey] = useState<number | null>(null);
  const attempts = useRef(0);

  // the first answer of the service becomes the table being edited (derived state, set while rendering)
  if (query.data && draft === null) setDraft(toDraft(query.data));

  useEffect(() => {
    if (focusKey !== null) document.getElementById(`prefix-${focusKey}`)?.focus();
  }, [focusKey]);

  const put = useMutation({
    mutationFn: (body: RiskRuleSet) => api.put<RiskRuleSet>(RISK_RULES, body),
    onSuccess: (set) => {
      queryClient.setQueryData(key, set);
      setDraft(toDraft(set));
      setSaved(set.version);
      setOutcome(null);
    },
    onError: async (error) => {
      attempts.current += 1;
      const refusal = describeRefusal(error);
      setSaved(null);
      setOutcome({ kind: 'refused', heading: refusal.heading, ...(refusal.detail ? { detail: refusal.detail } : {}), attempt: attempts.current });
      if (refusal.reload) {
        const fresh = await query.refetch();
        if (fresh.data) setDraft(toDraft(fresh.data));
      }
    },
  });

  function onSave() {
    if (!query.data || !draft || put.isPending) return;
    const invalid = draft.rows.flatMap((row, index) => (PREFIX.test(row.prefix) ? [] : [index]));
    if (invalid.length > 0) {
      attempts.current += 1;
      setSaved(null);
      setOutcome({ kind: 'invalid', rows: invalid, attempt: attempts.current });
      return;
    }
    setOutcome(null);
    put.mutate({ version: query.data.version, defaultTier: draft.defaultTier, rules: draft.rows.map((row) => ({ taxonomyPrefix: row.prefix, tier: row.tier })) });
  }

  function update(rowKey: number, change: Partial<Row>) {
    setDraft((current) => (current ? { ...current, rows: current.rows.map((row) => (row.key === rowKey ? { ...row, ...change } : row)) } : current));
  }

  function focusRow(event: MouseEvent<HTMLAnchorElement>, rowKey: number) {
    event.preventDefault();
    document.getElementById(`prefix-${rowKey}`)?.focus();
  }

  const invalidRows = outcome?.kind === 'invalid' ? outcome.rows : [];
  return (
    <>
      <PageHeading>Risk-tier rules</PageHeading>
      <p className="usa-intro">The table that turns a specialty prefix into a risk level. The legacy values are version 1.</p>
      {query.status === 'pending' && <p role="status">Loading risk rules</p>}
      {query.status === 'error' && (
        <>
          <Alert type="error" slim role="alert">
            <AlertText>The risk rules could not be loaded. Try again.</AlertText>
          </Alert>
          <Button type="button" onClick={() => void query.refetch()}>
            Retry
          </Button>
        </>
      )}
      {query.data && draft && (
        <>
          {saved !== null && (
            <Alert type="success" slim role="status">
              <AlertText>{`Risk rules saved as version ${saved}.`}</AlertText>
            </Alert>
          )}
          {outcome?.kind === 'refused' && (
            <ErrorSummary key={outcome.attempt} heading={outcome.heading}>
              {outcome.detail && <p>{outcome.detail}</p>}
            </ErrorSummary>
          )}
          {outcome?.kind === 'invalid' && draft && (
            <ErrorSummary key={outcome.attempt} heading={`${invalidRows.length} ${invalidRows.length === 1 ? 'error' : 'errors'} in the risk rules`}>
              <ul className="usa-list usa-list--unstyled">
                {invalidRows.map((index) => (
                  <li key={draft.rows[index]?.key}>
                    <a className="usa-link" href={`#prefix-${draft.rows[index]?.key}`} onClick={(event) => focusRow(event, draft.rows[index]?.key ?? 0)}>
                      {`Row ${index + 1}: ${BAD_PREFIX}`}
                    </a>
                  </li>
                ))}
              </ul>
            </ErrorSummary>
          )}
          <h2>{`Version ${query.data.version} (default tier: ${withCode(query.data.defaultTier, RISK_TIER_LABEL)})`}</h2>
          <p>
            A prefix is the first 3 characters of the specialty code (taxonomy) of a provider, for example 207. A provider whose specialty starts with a listed prefix gets that risk
            level; every other provider gets the default tier.
          </p>
          <FormGroup>
            <Label htmlFor="defaultTier">Default tier</Label>
            <div className="usa-hint" id="defaultTier-hint">
              Used for every provider whose specialty prefix is not in the table.
            </div>
            <Select id="defaultTier" name="defaultTier" aria-describedby="defaultTier-hint" value={draft.defaultTier} onChange={(event) => setDraft({ ...draft, defaultTier: event.target.value as Tier })}>
              {TIERS.map((tier) => (
                <option key={tier} value={tier}>
                  {withCode(tier, RISK_TIER_LABEL)}
                </option>
              ))}
            </Select>
          </FormGroup>
          <Table bordered={false} fullWidth>
            <caption className="usa-sr-only">Risk-tier rules by taxonomy prefix</caption>
            <thead>
              <tr>
                <th scope="col">Taxonomy prefix</th>
                <th scope="col">Tier</th>
                <th scope="col">Action</th>
              </tr>
            </thead>
            <tbody>
              {draft.rows.map((row, index) => {
                const invalid = invalidRows.includes(index);
                return (
                  <tr key={row.key}>
                    <td>
                      {invalid && (
                        <span id={`prefix-error-${row.key}`} className="usa-error-message">
                          {BAD_PREFIX}
                        </span>
                      )}
                      <TextInput
                        id={`prefix-${row.key}`}
                        name={`prefix-${row.key}`}
                        type="text"
                        value={row.prefix}
                        maxLength={3}
                        aria-label={`Taxonomy prefix, row ${index + 1}`}
                        aria-describedby={invalid ? `prefix-error-${row.key}` : undefined}
                        aria-invalid={invalid ? true : undefined}
                        validationStatus={invalid ? 'error' : undefined}
                        onChange={(event) => update(row.key, { prefix: event.target.value })}
                      />
                    </td>
                    <td>
                      <Select id={`tier-${row.key}`} name={`tier-${row.key}`} value={row.tier} aria-label={`Tier, row ${index + 1}`} onChange={(event) => update(row.key, { tier: event.target.value as Tier })}>
                        {TIERS.map((tier) => (
                          <option key={tier} value={tier}>
                            {withCode(tier, RISK_TIER_LABEL)}
                          </option>
                        ))}
                      </Select>
                    </td>
                    <td>
                      <Button
                        type="button"
                        outline
                        aria-label={`Remove row ${index + 1} (${row.prefix || 'empty'})`}
                        onClick={() => setDraft({ ...draft, rows: draft.rows.filter((candidate) => candidate.key !== row.key) })}
                      >
                        Remove
                        <span className="usa-sr-only">{` row ${index + 1} (${row.prefix || 'empty'})`}</span>
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </Table>
          {draft.rows.length === 0 && <p>No prefixes are listed, so every provider gets the default tier. Choose Add row to add one.</p>}
          <p>
            <Button
              type="button"
              outline
              onClick={() => {
                const added: Row = { key: (nextKey += 1), prefix: '', tier: 'L' };
                setDraft({ ...draft, rows: [...draft.rows, added] });
                setFocusKey(added.key);
              }}
            >
              Add row
            </Button>
          </p>
          <p className="usa-hint">Saving replaces the whole table with the rows above and gives it a new version number.</p>
          <Button type="button" onClick={onSave} disabled={put.isPending}>
            Save as new version
          </Button>
        </>
      )}
    </>
  );
}
