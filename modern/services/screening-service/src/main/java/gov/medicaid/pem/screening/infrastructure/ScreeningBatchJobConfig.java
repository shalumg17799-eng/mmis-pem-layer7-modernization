package gov.medicaid.pem.screening.infrastructure;

import gov.medicaid.pem.screening.application.ProviderListing;
import gov.medicaid.pem.screening.application.RunScreeningBatch;
import gov.medicaid.pem.screening.application.ScreeningBatchLauncher;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The batch screening as Spring Batch job PRVSCR01 (ADR-004; MAM-32) with the job parameters runId
 * and businessDate (D-020):
 *
 * <ol>
 *   <li>{@code screen}: every provider of provider-service, page by page; one item per provider;
 *       a due provider is screened (each screening commits on its own, so the step's chunks use a
 *       resourceless transaction); read and filter counts are kept by Spring Batch;
 *   <li>{@code results}: the SCRNOUT file and the run's counts, in one transaction.
 * </ol>
 *
 * A job that does not complete marks its run FAILED with the reason (AC-6). Runs are started on one
 * background thread, so the 202 answer is RUNNING (AC-1).
 */
@Configuration
public class ScreeningBatchJobConfig {

  /** The legacy program id, also BatchRun.job. */
  public static final String JOB = "PRVSCR01";

  private static final String SCREEN_STEP = "screen";

  private final ExecutorService thread =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "screening-batch"));

  @Bean
  Job screeningBatchJob(
      JobRepository repo, Step screeningBatchScreenStep, Step screeningBatchResultsStep,
      RunScreeningBatch batch) {
    return new JobBuilder(JOB, repo)
        .listener(failedRunListener(batch))
        .start(screeningBatchScreenStep)
        .next(screeningBatchResultsStep)
        .build();
  }

  @Bean
  Step screeningBatchScreenStep(
      JobRepository repo,
      ItemReader<ProviderListing.Row> screeningBatchReader,
      ItemProcessor<ProviderListing.Row, ProviderListing.Row> screeningBatchProcessor) {
    return new StepBuilder(SCREEN_STEP, repo)
        .<ProviderListing.Row, ProviderListing.Row>chunk(1, new ResourcelessTransactionManager())
        .reader(screeningBatchReader)
        .processor(screeningBatchProcessor)
        .writer(chunk -> {})
        .build();
  }

  @Bean
  Step screeningBatchResultsStep(
      JobRepository repo, PlatformTransactionManager tx, RunScreeningBatch batch) {
    return new StepBuilder("results", repo)
        .tasklet(
            (contribution, context) -> {
              StepExecution step = context.getStepContext().getStepExecution();
              var parameters = step.getJobParameters();
              batch.finish(
                  UUID.fromString(parameters.getString("runId")),
                  parameters.getLocalDate("businessDate"),
                  read(step.getJobExecution()));
              return RepeatStatus.FINISHED;
            },
            tx)
        .build();
  }

  /** Every provider, page after page of 200, until the last page (AC-2). */
  @Bean
  @StepScope
  ItemReader<ProviderListing.Row> screeningBatchReader(ProviderListing providers) {
    return new ItemReader<>() {
      private Iterator<ProviderListing.Row> current = Collections.emptyIterator();
      private int next;
      private boolean last;

      @Override
      public ProviderListing.Row read() {
        while (!current.hasNext()) {
          if (last) {
            return null;
          }
          ProviderListing.Page page = providers.page(next, ProviderListing.PAGE_SIZE);
          next++;
          current = page.items().iterator();
          last =
              page.items().size() < ProviderListing.PAGE_SIZE
                  || (long) next * ProviderListing.PAGE_SIZE >= page.total();
        }
        return current.next();
      }
    };
  }

  @Bean
  @StepScope
  ItemProcessor<ProviderListing.Row, ProviderListing.Row> screeningBatchProcessor(
      RunScreeningBatch batch, @Value("#{jobParameters['runId']}") String runId) {
    UUID id = UUID.fromString(runId);
    return p -> batch.screenIfDue(id, p) ? p : null;
  }

  @Bean
  ScreeningBatchLauncher screeningBatchLauncher(JobRepository repo, Job screeningBatchJob)
      throws Exception {
    TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
    launcher.setJobRepository(repo);
    launcher.setTaskExecutor(new TaskExecutorAdapter(thread));
    launcher.afterPropertiesSet();
    return (runId, businessDate) -> {
      try {
        launcher.run(
            screeningBatchJob,
            new JobParametersBuilder()
                .addString("runId", runId.toString())
                .addLocalDate("businessDate", businessDate)
                .toJobParameters());
      } catch (Exception e) {
        throw new IllegalStateException("job " + JOB + " not started: " + e.getMessage(), e);
      }
    };
  }

  private static int read(JobExecution job) {
    return job.getStepExecutions().stream()
        .filter(s -> SCREEN_STEP.equals(s.getStepName()))
        .mapToInt(s -> (int) s.getReadCount())
        .sum();
  }

  private static JobExecutionListener failedRunListener(RunScreeningBatch batch) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution job) {
        if (job.getStatus() == BatchStatus.COMPLETED) {
          return;
        }
        String runId = job.getJobParameters().getString("runId");
        LocalDate businessDate = job.getJobParameters().getLocalDate("businessDate");
        String reason =
            job.getAllFailureExceptions().stream()
                .findFirst()
                .map(e -> e.getMessage() == null ? e.toString() : e.getMessage())
                .orElse(job.getStatus().name());
        batch.failed(UUID.fromString(runId), businessDate, read(job), reason);
      }
    };
  }

  @jakarta.annotation.PreDestroy
  void shutdown() {
    thread.shutdown();
  }
}
