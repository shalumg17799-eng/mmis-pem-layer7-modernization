package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.tmsis.application.ExtractJobLauncher;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Starts a run's job on its own thread, so that the manual trigger answers 202 at once (ADR-004).
 * runId makes each run its own job instance; businessDate is the explicit business date (D-020).
 */
@Component
public class SpringBatchExtractLauncher implements ExtractJobLauncher {

  private final TaskExecutorJobLauncher launcher;
  private final Job job;

  public SpringBatchExtractLauncher(JobRepository repo, @Qualifier("tmsisExtractJob") Job job)
      throws Exception {
    this.launcher = new TaskExecutorJobLauncher();
    this.launcher.setJobRepository(repo);
    this.launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("tmsis-extract-"));
    this.launcher.afterPropertiesSet();
    this.job = job;
  }

  @Override
  public void launch(UUID runId, LocalDate businessDate) {
    JobParameters parameters =
        new JobParametersBuilder()
            .addString("runId", runId.toString())
            .addLocalDate("businessDate", businessDate)
            .toJobParameters();
    try {
      launcher.run(job, parameters);
    } catch (Exception e) {
      throw new IllegalStateException("job " + job.getName() + " not started: " + e.getMessage(), e);
    }
  }
}
