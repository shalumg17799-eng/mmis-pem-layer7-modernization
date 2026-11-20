// MAM-88 performance smoke: what search.js and detail.js share (settings, the NPI sample, the summary files).
//
// Inputs (all through the environment; nothing secret is ever written to a file or printed):
//   ANALYST_TOKEN  required. The bearer token of analyst1, set by tools/perf/run_perf_smoke.py in the environment of the
//                  docker compose process and passed through by the k6 service (infra/docker-compose.yml).
//   BASE_URL       the gateway; the k6 service sets http://pem-apps:8080 (the compose service name).
//   VUS            virtual users, default 20 (the NFR: 20 concurrent users).
//   DURATION       how long each scenario runs, default 60s.
//   THINK_MS       pause between two requests of one user, default 0: every user always has one request in flight, so there are
//                  20 concurrent requests for the whole run (the strictest reading of "20 concurrent users").
//   NPIS_FILE      default /out/npis.json: a sample of NPIs of the loaded set that the runner read back through the API.
//   SUMMARY_DIR    default /out/k6: where handleSummary writes <script>-summary.json.
import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { SharedArray } from 'k6/data';

export const BASE_URL = (__ENV.BASE_URL || 'http://pem-apps:8080').replace(/\/+$/, '');
export const VUS = parseInt(__ENV.VUS || '20', 10);
export const DURATION = __ENV.DURATION || '60s';
export const THINK_MS = parseInt(__ENV.THINK_MS || '0', 10);
export const SUMMARY_DIR = (__ENV.SUMMARY_DIR || '/out/k6').replace(/\/+$/, '');
export const TARGET_P95_MS = 500; // nfr.md, Performance: provider search / detail p95 under 500 ms at 20 concurrent users

// [{ npi, medicaidId, name }], one SharedArray so that 20 users do not each hold a copy
export const SAMPLE = new SharedArray('npi sample', function () {
  return JSON.parse(open(__ENV.NPIS_FILE || '/out/npis.json'));
});

export const PARAMS_BASE = { headers: { Accept: 'application/json', Authorization: 'Bearer ' + (__ENV.ANALYST_TOKEN || '') } };

export function params(kind, name) {
  return { headers: PARAMS_BASE.headers, tags: { kind: kind, name: name } };
}

export function pause() {
  if (THINK_MS > 0) sleep(THINK_MS / 1000);
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

/** One GET before the run: a missing token or an unusable stack aborts the run instead of "passing" on fast error answers. */
export function preflight() {
  if (!__ENV.ANALYST_TOKEN) exec.test.abort('ANALYST_TOKEN is not set');
  if (SAMPLE.length === 0) exec.test.abort('the NPI sample ' + (__ENV.NPIS_FILE || '/out/npis.json') + ' is empty');
  const r = http.get(BASE_URL + '/api/v1/providers?size=1', Object.assign({}, PARAMS_BASE, { tags: { name: 'preflight', phase: 'setup' } }));
  if (r.status !== 200) exec.test.abort('preflight GET /api/v1/providers?size=1 answered ' + r.status + ' (token or stack not usable)');
  return { total: r.json('total') };
}

/** The thresholds of one scenario: the p95 target, plus guards so that fast ERROR answers can never pass as a good latency. The
 * per-kind p95 lines only make k6 report those sub-metrics in the summary; their condition cannot fail. */
export function thresholdsFor(scenario, kinds) {
  const t = {};
  t['http_req_duration{scenario:' + scenario + '}'] = ['p(95)<' + TARGET_P95_MS];
  t['http_req_failed{scenario:' + scenario + '}'] = ['rate<0.01'];
  t['checks{scenario:' + scenario + '}'] = ['rate>0.99'];
  kinds.forEach(function (k) {
    t['http_req_duration{scenario:' + scenario + ',kind:' + k + '}'] = ['p(95)>=0'];
  });
  return t;
}

export function scenarioOptions(scenario, kinds) {
  const s = {};
  s[scenario] = { executor: 'constant-vus', vus: VUS, duration: DURATION, gracefulStop: '10s' };
  return {
    scenarios: s,
    thresholds: thresholdsFor(scenario, kinds),
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    userAgent: 'pem-perf-smoke-k6/MAM-88',
    tags: { story: 'MAM-88' },
  };
}

function ms(v) {
  return typeof v === 'number' ? v.toFixed(1) + ' ms' : 'n/a';
}

/** The summary of one script: the full k6 data as JSON (read by the runner) and four plain lines on stdout. */
export function summaryOf(script, scenario, data) {
  const m = data.metrics;
  const d = m['http_req_duration{scenario:' + scenario + '}'];
  const f = m['http_req_failed{scenario:' + scenario + '}'];
  const c = m['checks{scenario:' + scenario + '}'];
  const v = d ? d.values : {};
  const failed = Object.keys(m).filter(function (k) {
    const t = m[k].thresholds;
    return t && Object.keys(t).some(function (e) { return !t[e].ok; });
  });
  const out = {};
  out[SUMMARY_DIR + '/' + script + '-summary.json'] = JSON.stringify(data, null, 2);
  const lines = [
    '',
    script + ' (' + VUS + ' users, ' + DURATION + ', think time ' + THINK_MS + ' ms)',
    '  p95 ' + ms(v['p(95)']) + ' (target < ' + TARGET_P95_MS + ' ms)  avg ' + ms(v.avg) + '  med ' + ms(v.med) + '  p99 ' + ms(v['p(99)']) + '  max ' + ms(v.max),
    '  requests ' + (m.http_reqs ? m.http_reqs.values.count : 'n/a') + ' (' + (m.http_reqs ? m.http_reqs.values.rate.toFixed(1) : 'n/a') + '/s), failed ' + (f ? (f.values.rate * 100).toFixed(2) + ' %' : 'n/a') + ', checks passed ' + (c ? (c.values.rate * 100).toFixed(2) + ' %' : 'n/a'),
    '  thresholds: ' + (failed.length ? 'FAILED ' + failed.join(', ') : 'all ok'),
    '',
  ];
  if (!m.iterations || !m.iterations.values.count) {
    lines.splice(1, 4, script + ': NO REQUESTS WERE MADE (the run was aborted before the load started, see the k6 error above)');
  }
  out.stdout = lines.join('\n');
  return out;
}
