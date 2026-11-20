import type { UseQueryResult } from '@tanstack/react-query';
import type { Provider, ScreeningPage, SourceResult } from '../../../shared/api/types';
import { ENROLL_STATUS_LABEL, failedSourceText, providerName, RISK_TIER_LABEL, sourceName, withCode } from '../../../shared/format/format';

/** A passed check named by its source, in plain words (the sources of D-005). */
const passedWords = (result: SourceResult): string => sourceName(result.source);

/** A failed check named by its source and its error code, in full (D-016). */
const failedWords = (result: SourceResult): string => failedSourceText(result.source, result.errorCode);

/** The screening of the provider in one or two plain sentences: all passed, or which checks failed and which passed. */
export function screeningSentences(page: ScreeningPage): string[] {
  const latest = [...page.items].sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''))[0];
  const results = latest?.results ?? [];
  if (results.length === 0) return [];
  const passed = results.filter((result) => result.result === 'P');
  const failed = results.filter((result) => result.result === 'F');
  const conditional = results.filter((result) => result.result === 'C');
  const sentences: string[] = [];
  if (failed.length === 0 && conditional.length === 0) {
    return [`All ${results.length} checks passed: ${passed.map(passedWords).join(', ')}.`];
  }
  if (failed.length > 0) sentences.push(`${failed.length} of ${results.length} checks failed: ${failed.map(failedWords).join('; ')}.`);
  if (conditional.length > 0) sentences.push(`${conditional.length} check${conditional.length === 1 ? ' is' : 's are'} conditional: ${conditional.map(passedWords).join(', ')}.`);
  if (passed.length > 0) sentences.push(`${passed.length} passed: ${passed.map(passedWords).join(', ')}.`);
  return sentences;
}

/** The risk tier and where it comes from, from facts only: the tier is set from the first three characters of the taxonomy code (Q-005). */
export function riskSentence(provider: Provider): string {
  const prefix = provider.taxonomy.slice(0, 3);
  if (!provider.riskTier) return `No risk tier is assigned yet. It is set from the first three characters of the taxonomy code (${provider.taxonomy} -> ${prefix}).`;
  return `Risk tier ${withCode(provider.riskTier, RISK_TIER_LABEL)}: set from the first three characters of the taxonomy code (${provider.taxonomy} -> ${prefix}).`;
}

/** Who the provider is, as one line: "RIVERA MARIA, NPI 1003000111, Pending (P)". The NPI is always in full. */
export function whoSentence(provider: Provider): string {
  const name = providerName(provider);
  return `${name ? `${name}, ` : ''}NPI ${provider.npi}, ${withCode(provider.enrollStatus, ENROLL_STATUS_LABEL)}`;
}

/**
 * What the analyst needs before deciding, in plain words and above the screening results and the form (UX overhaul, item 4): who the
 * provider is, the screening in a sentence and the risk tier with its reason. Nothing here is new data: it restates the provider and
 * its screening results shown below it.
 */
export function ApprovalBrief({ provider, screenings }: { provider: Provider; screenings: UseQueryResult<ScreeningPage> }) {
  const sentences = screenings.status === 'success' ? screeningSentences(screenings.data) : [];
  return (
    <div className="usa-summary-box margin-y-3">
      <div className="usa-summary-box__body">
        <p className="text-bold margin-top-0">In short</p>
        <p className="text-bold">{whoSentence(provider)}</p>
        {sentences.map((sentence) => (
          <p key={sentence}>{sentence}</p>
        ))}
        <p className="margin-bottom-0">{riskSentence(provider)}</p>
      </div>
    </div>
  );
}
