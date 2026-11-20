package gov.medicaid.pem.revalidation.infrastructure;

import gov.medicaid.pem.revalidation.application.ExecuteSweep;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The revalidation sweep as a Spring Batch job (ADR-004, Q-102 A; PRVMTH01 STEP010 PRVREV01) with
 * the job parameters runId and businessDate (D-020). One step, "sweep", evaluates every tracked
 * provider in one transaction (cases, RevalidationDue events, counts and REVALQ file together) and
 * records its counts in the Spring Batch tables (read = providers evaluated, write = cases opened,
 * filter = providers not queued). A job that does not complete marks its run FAILED with the reason
 * (D-024).
 */
@Configuration
public class SweepJobConfig {

  /** The job name, also BatchRun.job of the API. */
  public static final String JOB = "revalidation-sweep";

  @Bean
  Job revalidationSweepJob(JobRepository repo, Step revalidationSweepStep, ExecuteSweep sweep) {
    return new JobBuilder(JOB, repo)
        .listener(failedRunListener(sweep))
        .start(revalidationSweepStep)
        .build();
  }

  @Bean
  Step revalidationSweepStep(
      JobRepository repo, PlatformTransactionManager tx, ExecuteSweep sweep) {
    return new StepBuilder("sweep", repo)
        .tasklet(
            (contribution, context) -> {
              var parameters = context.getStepContext().getStepExecution().getJobParameters();
              ExecuteSweep.Result r =
                  sweep.execute(
                      UUID.fromString(parameters.getString("runId")),
                      parameters.getLocalDate("businessDate"));
              for (int i = 0; i < r.evaluated(); i++) {
                contribution.incrementReadCount();
              }
              contribution.incrementWriteCount(r.queued());
              contribution.incrementFilterCount(r.evaluated() - r.queued());
              return RepeatStatus.FINISHED;
            },
            tx)
        .build();
  }

  private static JobExecutionListener failedRunListener(ExecuteSweep sweep) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution job) {
        if (job.getStatus() == BatchStatus.COMPLETED) {
          return;
        }
        String runId = job.getJobParameters().getString("runId");
        LocalDate businessDate = job.getJobParameters().getLocalDate("businessDate");
        sweep.failed(UUID.fromString(runId), businessDate, reason(job));
      }
    };
  }

  /** The first failure's message, as the run recorded it before Spring Batch (MAM-45). */
  private static String reason(JobExecution job) {
    return job.getAllFailureExceptions().stream()
        .findFirst()
        .map(e -> e.getMessage() == null ? e.toString() : e.getMessage())
        .orElse(job.getExitStatus().getExitDescription().isBlank()
            ? job.getStatus().name()
            : job.getExitStatus().getExitDescription());
  }
}
