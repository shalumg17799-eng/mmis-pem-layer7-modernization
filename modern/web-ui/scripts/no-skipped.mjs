// Fails the test run when any test was skipped or left as todo (tdd-workflow 4: zero skipped tests).
import { readFileSync } from 'node:fs';

const report = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const skipped = report.testResults
  .flatMap((f) => f.assertionResults.map((a) => ({ file: f.name, ...a })))
  .filter((a) => a.status === 'skipped' || a.status === 'pending' || a.status === 'todo');
if (skipped.length > 0) {
  for (const s of skipped) console.error(`SKIPPED: ${s.fullName} (${s.file})`);
  console.error(`${skipped.length} skipped test(s): a test cannot be skipped to get to green.`);
  process.exit(1);
}
console.log(`no skipped tests (${report.numTotalTests} tests)`);
