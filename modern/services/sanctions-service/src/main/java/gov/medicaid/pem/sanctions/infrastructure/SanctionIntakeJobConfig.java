package gov.medicaid.pem.sanctions.infrastructure;

import gov.medicaid.pem.sanctions.application.SanctionIntake;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.stereotype.Component;

/**
 * The SANCIN intake as Spring Batch job PRVSAN01 (ADR-004; MAM-55) with the job parameters runId,
 * businessDate (D-020) and recordedBy. One step, "apply", applies the records; each record commits
 * on its own (RecordSanction), so the step uses a resourceless transaction and a failure keeps the
 * records before it (AC-6). A job that does not complete marks its run FAILED with the reason. The
 * uploaded file is handed to the job in memory; runs are started on one background thread.
 */
@Component
public class SanctionIntakeJobConfig
    implements gov.medicaid.pem.sanctions.application.SanctionIntakeLauncher, DisposableBean {

  /** The legacy program id, also BatchRun.job. */
  public static final String JOB = "PRVSAN01";

  private final Map<String, byte[]> files = new ConcurrentHashMap<>();
  private final ExecutorService thread =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "sanction-intake"));
  private final TaskExecutorJobLauncher launcher;
  private final Job job;

  public SanctionIntakeJobConfig(JobRepository repo, SanctionIntake intake) throws Exception {
    Step apply =
        new StepBuilder("apply", repo)
            .tasklet(
                (contribution, context) -> {
                  var p = context.getStepContext().getStepExecution().getJobParameters();
                  String runId = p.getString("runId");
                  byte[] file = files.remove(runId);
                  if (file == null) {
                    throw new IllegalStateException(
                        "the uploaded file of run " + runId + " is gone");
                  }
                  intake.process(
                      UUID.fromString(runId),
                      p.getLocalDate("businessDate"),
                      file,
                      p.getString("recordedBy"));
                  return RepeatStatus.FINISHED;
                },
                new ResourcelessTransactionManager())
            .build();
    this.job =
        new JobBuilder(JOB, repo).listener(failedRunListener(intake, files)).start(apply).build();
    this.launcher = new TaskExecutorJobLauncher();
    this.launcher.setJobRepository(repo);
    this.launcher.setTaskExecutor(new TaskExecutorAdapter(thread));
    this.launcher.afterPropertiesSet();
  }

  @Override
  public void launch(UUID runId, LocalDate businessDate, byte[] file, String recordedBy) {
    files.put(runId.toString(), file);
    try {
      launcher.run(
          job,
          new JobParametersBuilder()
              .addString("runId", runId.toString())
              .addLocalDate("businessDate", businessDate)
              .addString("recordedBy", recordedBy, false)
              .toJobParameters());
    } catch (Exception e) {
      files.remove(runId.toString());
      throw new SanctionIntake.RunFailure("job " + JOB + " not started: ", e);
    }
  }

  private static JobExecutionListener failedRunListener(
      SanctionIntake intake, Map<String, byte[]> files) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution job) {
        String runId = job.getJobParameters().getString("runId");
        files.remove(runId);
        if (job.getStatus() == BatchStatus.COMPLETED) {
          return;
        }
        LocalDate businessDate = job.getJobParameters().getLocalDate("businessDate");
        job.getAllFailureExceptions().stream()
            .findFirst()
            .ifPresentOrElse(
                failure -> intake.failed(UUID.fromString(runId), businessDate, failure),
                () -> intake.failed(UUID.fromString(runId), businessDate, job.getStatus().name()));
      }
    };
  }

  @Override
  public void destroy() {
    thread.shutdown();
  }
}
