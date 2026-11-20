package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.ArchiveExtract;
import gov.medicaid.pem.tmsis.application.ExtractItem;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
import gov.medicaid.pem.tmsis.application.FailureReason;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import gov.medicaid.pem.tmsis.domain.ReportingPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The T-MSIS extract as a Spring Batch job (ADR-004; PRVMTH01 STEP020 PRVTMS01 and STEP030
 * IEBGENER), with the job parameters runId and businessDate (D-020):
 *
 * <ol>
 *   <li>{@code extract}: provider-service's ACTIVE providers, page by page, become TMSISOUT records
 *       with the run's YYDDD period (chunks of 200, stored in extract_row);
 *   <li>{@code archive}: the file is written in NPI order and archived as the next generation, and
 *       the run is COMPLETED.
 * </ol>
 *
 * Read, filter and write counts are kept in the Spring Batch tables; a job that does not complete
 * marks its run FAILED with the reason (D-024).
 */
@Configuration
public class TmsisExtractJobConfig {

  private static final Logger LOG = LoggerFactory.getLogger(TmsisExtractJobConfig.class);

  /** The legacy program id, also the job name shown in BatchRun.job (MAM-75 AC-1). */
  public static final String JOB = "PRVTMS01";

  @Bean
  Job tmsisExtractJob(
      JobRepository repo,
      Step tmsisExtractStep,
      Step tmsisArchiveStep,
      ExtractRuns runs,
      Clock clock) {
    return new JobBuilder(JOB, repo)
        .listener(failedRunListener(runs, clock))
        .start(tmsisExtractStep)
        .next(tmsisArchiveStep)
        .build();
  }

  @Bean
  Step tmsisExtractStep(
      JobRepository repo,
      PlatformTransactionManager tx,
      ProviderPagesReader tmsisProviderReader,
      ItemProcessor<ProviderRow, ExtractItem> tmsisRecordProcessor,
      ItemWriter<ExtractItem> tmsisRowWriter) {
    return new StepBuilder("extract", repo)
        .<ProviderRow, ExtractItem>chunk(ActiveProviders.PAGE_SIZE, tx)
        .reader(tmsisProviderReader)
        .processor(tmsisRecordProcessor)
        .writer(tmsisRowWriter)
        .build();
  }

  @Bean
  Step tmsisArchiveStep(JobRepository repo, PlatformTransactionManager tx, ArchiveExtract archive) {
    return new StepBuilder("archive", repo)
        .tasklet(
            (contribution, context) -> {
              Object runId = context.getStepContext().getJobParameters().get("runId");
              archive.archive(UUID.fromString(String.valueOf(runId)));
              return RepeatStatus.FINISHED;
            },
            tx)
        .build();
  }

  @Bean
  @StepScope
  ProviderPagesReader tmsisProviderReader(ActiveProviders providers) {
    return new ProviderPagesReader(providers);
  }

  /**
   * Only ACTIVE providers give a record (filtered items are counted by Spring Batch); a later
   * enrolment year is flagged and logged (MAM-78).
   */
  @Bean
  @StepScope
  ItemProcessor<ProviderRow, ExtractItem> tmsisRecordProcessor(
      @Value("#{jobParameters['businessDate']}") LocalDate businessDate) {
    String period = ReportingPeriod.yyddd(businessDate);
    return p -> ExtractItem.of(p, businessDate, period).orElse(null);
  }

  @Bean
  @StepScope
  ItemWriter<ExtractItem> tmsisRowWriter(
      ExtractRuns runs, @Value("#{jobParameters['runId']}") String runId) {
    UUID id = UUID.fromString(runId);
    return chunk -> {
      List<ExtractItem> items = new ArrayList<>(chunk.getItems());
      runs.addRows(id, items.stream().map(ExtractItem::record).toList());
      runs.addFutureEnrolments(
          id, (int) items.stream().filter(ExtractItem::futureEnrolment).count());
    };
  }

  private static JobExecutionListener failedRunListener(ExtractRuns runs, Clock clock) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution job) {
        if (job.getStatus() == BatchStatus.COMPLETED) {
          return;
        }
        String runId = job.getJobParameters().getString("runId");
        List<Throwable> failures = job.getAllFailureExceptions();
        Throwable first = failures.isEmpty() ? null : failures.get(0);
        String reason = first == null ? job.getStatus().name() : FailureReason.of(first);
        LOG.error("PRVTMS01 run {} FAILED: {}", runId, reason, first);
        runs.fail(UUID.fromString(runId), reason, clock.instant());
      }
    };
  }
}
