# WireMock stubs of the external screening sources (MAM-47)

Local look-alikes of the five outside sources that screening checks (D-005). The real APIs are wired in a later release;
until then the request and response are our own simple JSON, documented here. Synthetic data only: every hit is either a
sample from `aws-transform/test-data/` or a fabricated NPI with a valid check digit that is never seeded.

- Base URL: `http://localhost:8280` from the host, `http://wiremock:8280` inside the stack (`PEM_SOURCES_URL`).
- Mappings: `mappings/<source>/*.json`, one folder per source. Every mapping carries `metadata.source` and
  `metadata.answer` (`hit`, `clean`, `error`, `slow`, and `not-found` for the licence board).
- Reload after editing a mapping: `curl -X POST http://localhost:8280/__admin/mappings/reset`, or
  `docker compose -p pem restart wiremock`.
- Not matched (wrong path, missing or malformed parameter): WireMock's own 404. Every real answer, including "clean" and
  "not found", is HTTP 200 with a JSON body, so the two can never be confused.

## Sources

| Source | Request | Clean answer | Hit |
|---|---|---|---|
| leie (OIG LEIE exclusions) | `GET /leie/exclusions?npi={10 digits}` | `excluded: false` | NPI 1990000111 excluded, type 1128(a)(1) PROGRAM-RELATED FRAUD, 2023-01-15 (the owner of applicant 1003000444) |
| sam (SAM.gov debarments) | `GET /sam/debarments?npi={10 digits}` | `debarred: false` (SAM is empty by design) | NPI 1005999010, the fabricated entity SAMPLE DEBARRED SUPPLY 01, debarred from 2025-06-01 |
| nppes (national registry) | `GET /nppes/providers?npi={10 digits}` | `found: true, active: true` | NPI 1005999036, fabricated, deactivated 2024-06-30 |
| license-board (state licences) | `GET /license-board/licenses?licenseNo={no}&state={st}` | the six ACTIVE sample licences of `license_board.txt` with their expiry, and the seeded licences SD10001-SD10007 / TX and SD20001 / TX, the licence of the valid intake individual 1006133288 of `testdata/fixed-width/appinput-baseline.dat` (all ACTIVE, expiry 2030-12) | PT99900 / TX EXPIRED, expiry 2024-01. Any other licence or state answers `found: false` |
| ssa-dmf (death master file) | `GET /ssa-dmf/deaths?npi={10 digits}` | `deceased: false` | NPI 1005999028, fabricated, died 2025-08-01 |

## Selecting the error and slow answers

Send the request header `X-Pem-Stub` on any call to any source:

- `X-Pem-Stub: error`: HTTP 500 with `{"source": "<code>", "error": "stubbed server error"}`.
- `X-Pem-Stub: slow`: the clean answer after a fixed delay of **6000 ms** (longer than the 5 s screening budget). On the
  licence board the slow answer is always ACTIVE, expiry 2030-12, for the licence asked.

## Documented calls

Each row is called by `modern/tools/checks/tests/test_wiremock_stubs.py`, which checks the exact status and body.

| Source | Answer | Request | Header | Status | Response |
|---|---|---|---|---|---|
| leie | hit | `GET /leie/exclusions?npi=1990000111` | - | 200 | `{"source":"OIGLEIE","npi":"1990000111","excluded":true,"exclusionType":"1128(a)(1)","description":"PROGRAM-RELATED FRAUD","exclusionDate":"2023-01-15"}` |
| leie | clean | `GET /leie/exclusions?npi=1003000111` | - | 200 | `{"source":"OIGLEIE","npi":"1003000111","excluded":false}` |
| leie | error | `GET /leie/exclusions?npi=1003000111` | `X-Pem-Stub: error` | 500 | `{"source":"OIGLEIE","error":"stubbed server error"}` |
| leie | slow | `GET /leie/exclusions?npi=1003000111` | `X-Pem-Stub: slow` | 200 | `{"source":"OIGLEIE","npi":"1003000111","excluded":false}` |
| sam | hit | `GET /sam/debarments?npi=1005999010` | - | 200 | `{"source":"SAMGOV","npi":"1005999010","debarred":true,"entityName":"SAMPLE DEBARRED SUPPLY 01","debarredFrom":"2025-06-01"}` |
| sam | clean | `GET /sam/debarments?npi=1003000111` | - | 200 | `{"source":"SAMGOV","npi":"1003000111","debarred":false}` |
| sam | error | `GET /sam/debarments?npi=1003000111` | `X-Pem-Stub: error` | 500 | `{"source":"SAMGOV","error":"stubbed server error"}` |
| sam | slow | `GET /sam/debarments?npi=1003000111` | `X-Pem-Stub: slow` | 200 | `{"source":"SAMGOV","npi":"1003000111","debarred":false}` |
| nppes | hit | `GET /nppes/providers?npi=1005999036` | - | 200 | `{"source":"NPPES","npi":"1005999036","found":true,"active":false,"deactivationDate":"2024-06-30"}` |
| nppes | clean | `GET /nppes/providers?npi=1003000111` | - | 200 | `{"source":"NPPES","npi":"1003000111","found":true,"active":true}` |
| nppes | error | `GET /nppes/providers?npi=1003000111` | `X-Pem-Stub: error` | 500 | `{"source":"NPPES","error":"stubbed server error"}` |
| nppes | slow | `GET /nppes/providers?npi=1003000111` | `X-Pem-Stub: slow` | 200 | `{"source":"NPPES","npi":"1003000111","found":true,"active":true}` |
| ssa-dmf | hit | `GET /ssa-dmf/deaths?npi=1005999028` | - | 200 | `{"source":"SSADMF","npi":"1005999028","deceased":true,"dateOfDeath":"2025-08-01"}` |
| ssa-dmf | clean | `GET /ssa-dmf/deaths?npi=1003000111` | - | 200 | `{"source":"SSADMF","npi":"1003000111","deceased":false}` |
| ssa-dmf | error | `GET /ssa-dmf/deaths?npi=1003000111` | `X-Pem-Stub: error` | 500 | `{"source":"SSADMF","error":"stubbed server error"}` |
| ssa-dmf | slow | `GET /ssa-dmf/deaths?npi=1003000111` | `X-Pem-Stub: slow` | 200 | `{"source":"SSADMF","npi":"1003000111","deceased":false}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=MD12345&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"MD12345","state":"TX","found":true,"status":"ACTIVE","expiry":"2028-09"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=DM55500&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"DM55500","state":"TX","found":true,"status":"ACTIVE","expiry":"2027-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=HS22200&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"HS22200","state":"TX","found":true,"status":"ACTIVE","expiry":"2029-01"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=GR11100&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"GR11100","state":"TX","found":true,"status":"ACTIVE","expiry":"2028-06"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=HP30300&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"HP30300","state":"TX","found":true,"status":"ACTIVE","expiry":"2029-11"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=AM44400&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"AM44400","state":"TX","found":true,"status":"ACTIVE","expiry":"2028-05"}` |
| license-board | hit | `GET /license-board/licenses?licenseNo=PT99900&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"PT99900","state":"TX","found":true,"status":"EXPIRED","expiry":"2024-01"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10001&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10001","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10002&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10002","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10003&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10003","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10004&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10004","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10005&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10005","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10006&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10006","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | clean | `GET /license-board/licenses?licenseNo=SD10007&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"SD10007","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
| license-board | not-found | `GET /license-board/licenses?licenseNo=XX00000&state=TX` | - | 200 | `{"source":"LICBOARD","licenseNo":"XX00000","state":"TX","found":false}` |
| license-board | error | `GET /license-board/licenses?licenseNo=MD12345&state=TX` | `X-Pem-Stub: error` | 500 | `{"source":"LICBOARD","error":"stubbed server error"}` |
| license-board | slow | `GET /license-board/licenses?licenseNo=MD12345&state=TX` | `X-Pem-Stub: slow` | 200 | `{"source":"LICBOARD","licenseNo":"MD12345","state":"TX","found":true,"status":"ACTIVE","expiry":"2030-12"}` |
