import { Link } from 'react-router-dom';
import { useSession } from '../../../shared/auth/AuthProvider';

interface Tool {
  to: string;
  text: string;
  detail: string;
}

/**
 * "Tools" of the supervisor and the administrator, on their start page (the provider search): the other screens their role may
 * open, each with one line on what it is for. The wording differs from the menu entries on purpose, so that every link of a page
 * has its own name. The menu is unchanged; this panel only helps to find the screens. Nothing is shown to an analyst.
 */
export function ToolsPanel() {
  const user = useSession();
  const tools: Tool[] = [];
  if (user.roles.includes('SUPERVISOR')) {
    tools.push({ to: '/console/queues', text: 'Review providers waiting for a decision', detail: 'The four lists of the day: approvals, failed screenings, revalidations and rejected applications.' });
  }
  if (user.roles.includes('ADMIN')) {
    tools.push({ to: '/console/admin/jobs', text: 'Run legacy jobs and download their files', detail: 'Start a job of the old system for a business date and save the files it produced.' });
    tools.push({ to: '/console/admin/risk-rules', text: 'Change how risk levels are assigned', detail: 'See the table that turns a specialty prefix into a risk level, and save a new version.' });
  }
  if (tools.length === 0) return null;
  return (
    <section className="usa-summary-box margin-y-3" aria-labelledby="tools-heading">
      <div className="usa-summary-box__body">
        <h2 className="usa-summary-box__heading" id="tools-heading">
          Tools
        </h2>
        <ul className="usa-list usa-list--unstyled">
          {tools.map((tool) => (
            <li key={tool.to} className="margin-bottom-1">
              <Link className="usa-link text-bold" to={tool.to}>
                {tool.text}
              </Link>
              <br />
              <span>{tool.detail}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
