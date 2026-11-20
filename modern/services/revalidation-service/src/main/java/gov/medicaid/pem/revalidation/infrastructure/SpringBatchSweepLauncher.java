package gov.medicaid.pem.revalidation.infrastructure;

import gov.medicaid.pem.revalidation.application.SweepJobLauncher;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.stereotype.Component;

/**
 * Starts a sweep's job in the background on one thread, so that runs never overlap (as the
 * single-thread executor of MAM-45 did) and the manual trigger answers 202 at once. runId makes
 * each run its own job instance; businessDate is the explicit business date (D-020).
 */
@Component
public class SpringBatchSweepLauncher implements SweepJobLauncher, DisposableBean {

  private final ExecutorService thread =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "revalidation-sweep"));
  private final TaskExecutorJobLauncher launcher;
  private final Job job;

  public SpringBatchSweepLauncher(JobRepository repo, @Qualifier("revalidationSweepJob") Job job)
      throws Exception {
    this.launcher = new TaskExecutorJobLauncher();
    this.launcher.setJobRepository(repo);
    this.launcher.setTaskExecutor(new TaskExecutorAdapter(thread));
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
      throw new IllegalStateException(
          "job " + job.getName() + " not started: " + e.getMessage(), e);
    }
  }

  @Override
  public void destroy() {
    thread.shutdown();
  }
}
