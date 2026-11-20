import { reviewRows, type FormValues } from './applicationForm';

/**
 * "Review your answers": a live, read-only summary of what is typed, just above Submit application. It sits on the same page as the
 * form (one click on Submit application still sends the request, MAM-64) and has no control of its own, so the tab order of the form
 * is unchanged. It shows no error and no code: the checks stay with the service.
 */
export function ReviewSummary({ values }: { values: FormValues }) {
  return (
    <section className="usa-summary-box margin-y-3" aria-labelledby="review-heading">
      <div className="usa-summary-box__body">
        <h2 className="usa-summary-box__heading" id="review-heading">
          Review your answers
        </h2>
        <dl className="margin-0">
          {reviewRows(values).map(([label, value]) => (
            <div key={label} className="margin-bottom-1">
              <dt className="text-bold display-inline">{label}: </dt>
              <dd className="margin-0 display-inline">{value}</dd>
            </div>
          ))}
        </dl>
        <p className="margin-bottom-0">Nothing is sent until you choose Submit application.</p>
      </div>
    </section>
  );
}
