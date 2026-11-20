package gov.medicaid.pem.directory.infrastructure;

import gov.medicaid.pem.directory.application.DirectoryExtractJobLauncher;
import gov.medicaid.pem.directory.application.RunDirectoryExtract;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/**
 * The directory extract as a Spring Batch job PRVDIR01 (ADR-004; MAM-67) with the job parameters
 * runId and businessDate (D-020). One step, "extract", writes the DIROUT file and completes the run
 * in its transaction (write count = records). A job that does not complete marks its run FAILED
 * with a short reason (D-024) and logs the whole exception. The launcher runs the job on the
 * caller's thread, so the 202 answer carries the finished run (AC-1).
 */
@Configuration
public class DirectoryExtractJobConfig {

  /** The legacy program id, also BatchRun.job. */
  public static final String JOB = "PRVDIR01";

  @Bean
  Job directoryExtractJob(
      JobRepository repo, Step directoryExtractStep, RunDirectoryExtract extract) {
    return new JobBuilder(JOB, repo)
        .listener(failedRunListener(extract))
        .start(directoryExtractStep)
        .build();
  }

  @Bean
  Step directoryExtractStep(
      JobRepository repo, PlatformTransactionManager tx, RunDirectoryExtract extract) {
    return new StepBuilder("extract", repo)
        .tasklet(
            (contribution, context) -> {
              var parameters = context.getStepContext().getStepExecution().getJobParameters();
              int rows =
                  extract.execute(
                      UUID.fromString(parameters.getString("runId")),
                      parameters.getLocalDate("businessDate"));
              contribution.incrementWriteCount(rows);
              return RepeatStatus.FINISHED;
            },
            tx)
        .transactionAttribute(repeatableRead())
        .build();
  }

  @Bean
  DirectoryExtractJobLauncher directoryExtractJobLauncher(
      JobRepository repo, Job directoryExtractJob) throws Exception {
    TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(repo);
    launcher.setTaskExecutor(new SyncTaskExecutor());
    launcher.afterPropertiesSet();
    return (runId, businessDate) -> {
      try {
        launcher.run(
            directoryExtractJob,
            new JobParametersBuilder()
                .addString("runId", runId.toString())
                .addLocalDate("businessDate", businessDate)
                .toJobParameters());
      } catch (Exception e) {
        throw new IllegalStateException("job " + JOB + " not started: " + e.getMessage(), e);
      }
    };
  }

  /** One snapshot for the whole read of the extract, see RunDirectoryExtract.execute. */
  private static DefaultTransactionAttribute repeatableRead() {
    DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
    attribute.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return attribute;
  }

  private static JobExecutionListener failedRunListener(RunDirectoryExtract extract) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution job) {
        if (job.getStatus() == BatchStatus.COMPLETED) {
          return;
        }
        UUID runId = UUID.fromString(job.getJobParameters().getString("runId"));
        LocalDate businessDate = job.getJobParameters().getLocalDate("businessDate");
        job.getAllFailureExceptions().stream()
            .findFirst()
            .ifPresentOrElse(
                cause -> extract.failed(runId, businessDate, cause),
                () -> extract.failed(runId, businessDate, job.getStatus().name()));
      }
    };
  }
}
