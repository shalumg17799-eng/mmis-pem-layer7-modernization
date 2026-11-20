package gov.medicaid.pem.enrollment.infrastructure;

import gov.medicaid.pem.enrollment.application.IntakeLauncher;
import gov.medicaid.pem.enrollment.application.IntakeLoad;
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
 * The APPINPUT intake as Spring Batch job PRVENR01 (ADR-004; MAM-56, MAM-57) with the job
 * parameters runId, businessDate (D-020) and submittedBy. One step, "load", processes the records;
 * each application commits on its own (SubmitApplication), so the step uses a resourceless
 * transaction and a failure keeps the records before it. A job that does not complete marks its run
 * FAILED with the reason. The uploaded file is handed to the job in memory; runs are started on one
 * background thread.
 */
@Component
public class IntakeJobConfig implements IntakeLauncher, DisposableBean {

  /** The legacy program id, also BatchRun.job. */
  public static final String JOB = "PRVENR01";

  private final Map<String, byte[]> files = new ConcurrentHashMap<>();
  private final ExecutorService thread =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "intake-load"));
  private final TaskExecutorJobLauncher launcher;
  private final Job job;

  public IntakeJobConfig(JobRepository repo, IntakeLoad load) throws Exception {
    Step step =
        new StepBuilder("load", repo)
            .tasklet(
                (contribution, context) -> {
                  var p = context.getStepContext().getStepExecution().getJobParameters();
                  String runId = p.getString("runId");
                  byte[] file = files.remove(runId);
                  if (file == null) {
                    throw new IllegalStateException("the uploaded file of run " + runId + " is gone");
                  }
                  load.process(
                      UUID.fromString(runId),
                      p.getLocalDate("businessDate"),
                      file,
                      p.getString("submittedBy"));
                  return RepeatStatus.FINISHED;
                },
                new ResourcelessTransactionManager())
            .build();
    this.job = new JobBuilder(JOB, repo).listener(failedRunListener(load, files)).start(step).build();
    this.launcher = new TaskExecutorJobLauncher();
    this.launcher.setJobRepository(repo);
    this.launcher.setTaskExecutor(new TaskExecutorAdapter(thread));
    this.launcher.afterPropertiesSet();
  }

  @Override
  public void launch(UUID runId, LocalDate businessDate, byte[] file, String submittedBy) {
    files.put(runId.toString(), file);
    try {
      launcher.run(
          job,
          new JobParametersBuilder()
              .addString("runId", runId.toString())
              .addLocalDate("businessDate", businessDate)
              .addString("submittedBy", submittedBy, false)
              .toJobParameters());
    } catch (Exception e) {
      files.remove(runId.toString());
      throw new IllegalStateException("job " + JOB + " not started: " + e.getMessage(), e);
    }
  }

  private static JobExecutionListener failedRunListener(IntakeLoad load, Map<String, byte[]> files) {
    return new JobExecutionListener() {
      @Override
      public void afterJob(JobExecution job) {
        String runId = job.getJobParameters().getString("runId");
        files.remove(runId);
        if (job.getStatus() == BatchStatus.COMPLETED) {
          return;
        }
        String reason =
            job.getAllFailureExceptions().stream()
                .findFirst()
                .map(e -> e.getMessage() == null ? e.toString() : e.getMessage())
                .orElse(job.getStatus().name());
        load.failed(
            UUID.fromString(runId), job.getJobParameters().getLocalDate("businessDate"), reason);
      }
    };
  }

  @Override
  public void destroy() {
    thread.shutdown();
  }
}
