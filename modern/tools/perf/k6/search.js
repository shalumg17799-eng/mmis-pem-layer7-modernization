// MAM-88 AC-2, scenario 1: GET /api/v1/providers (the analyst console's provider list and search), 20 users, p95 under 500 ms.
// Run by tools/perf/run_perf_smoke.py:  k6 run /scripts/search.js   (inputs and thresholds: see common.js)
//
// What one user does on each pass (random choice, weights in percent):
//   60  a page of the list, any page of the whole set:                GET /api/v1/providers?page=<0..last>&size=20     (offset paging, contract page and size)
//   20  a work-queue filter by enrolment status, first pages:         GET /api/v1/providers?status=<P|A|T|D|S>&page=<0..4>&size=20
//   10  an exact lookup by NPI (the PINQ inquiry):                    GET /api/v1/providers?npi=<npi of the loaded set>
//    5  an exact lookup by Medicaid ID (the legacy alternate index):  GET /api/v1/providers?medicaidId=<id of the loaded set>
//    5  a name search (contains):                                     GET /api/v1/providers?name=<name of the loaded set>&size=20
// The p95 of the scenario is taken over all of them together (that is the target); the kind of each request is a tag, so the
// summary also shows the p95 per kind and a slow kind cannot hide.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SAMPLE, params, pause, pick, preflight, scenarioOptions, summaryOf } from './common.js';

const KINDS = ['page', 'status', 'npi', 'medicaidId', 'name'];
const STATUSES = ['P', 'A', 'T', 'D', 'S'];
const SIZE = 20;

export const options = scenarioOptions('search', KINDS);

export function setup() {
  return preflight();
}

export default function (data) {
  const pages = Math.max(1, Math.ceil(data.total / SIZE));
  const roll = Math.random() * 100;
  let kind;
  let url;
  if (roll < 60) {
    kind = 'page';
    url = '/api/v1/providers?page=' + Math.floor(Math.random() * pages) + '&size=' + SIZE;
  } else if (roll < 80) {
    kind = 'status';
    url = '/api/v1/providers?status=' + pick(STATUSES) + '&page=' + Math.floor(Math.random() * 5) + '&size=' + SIZE;
  } else if (roll < 90) {
    kind = 'npi';
    url = '/api/v1/providers?npi=' + pick(SAMPLE).npi;
  } else if (roll < 95) {
    kind = 'medicaidId';
    url = '/api/v1/providers?medicaidId=' + pick(SAMPLE).medicaidId;
  } else {
    kind = 'name';
    url = '/api/v1/providers?name=' + encodeURIComponent(pick(SAMPLE).name) + '&size=' + SIZE;
  }
  const r = http.get(BASE_URL + url, params(kind, 'GET /api/v1/providers (' + kind + ')'));
  check(r, {
    'status is 200': (x) => x.status === 200,
    'answer has items and total': (x) => {
      try {
        const b = x.json();
        return Array.isArray(b.items) && typeof b.total === 'number';
      } catch (e) {
        return false;
      }
    },
  });
  pause();
}

export function handleSummary(data) {
  return summaryOf('search', 'search', data);
}
