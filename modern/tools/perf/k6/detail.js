// MAM-88 AC-2, scenario 2: GET /api/v1/providers/{npi} (one provider record, the PINQ lookup), 20 users, p95 under 500 ms.
// Run by tools/perf/run_perf_smoke.py:  k6 run /scripts/detail.js   (inputs and thresholds: see common.js)
// Every request asks for a random NPI of the sample of the loaded set (/out/npis.json, read back through the API by the runner),
// so the answers come from the 10,000-row table, not from one hot row.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SAMPLE, params, pause, pick, preflight, scenarioOptions, summaryOf } from './common.js';

export const options = scenarioOptions('detail', ['detail']);

export function setup() {
  return preflight();
}

export default function () {
  const npi = pick(SAMPLE).npi;
  const r = http.get(BASE_URL + '/api/v1/providers/' + npi, params('detail', 'GET /api/v1/providers/{npi}'));
  check(r, {
    'status is 200': (x) => x.status === 200,
    'answer is the requested provider': (x) => {
      try {
        return x.json('npi') === npi;
      } catch (e) {
        return false;
      }
    },
  });
  pause();
}

export function handleSummary(data) {
  return summaryOf('detail', 'detail', data);
}
