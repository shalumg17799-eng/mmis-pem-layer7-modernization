package gov.medicaid.pem.screening.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.screening.application.ProviderFacts;
import gov.medicaid.pem.screening.application.ScreeningSources;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import gov.medicaid.pem.screening.domain.SourceRules;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The four sources over HTTP (the simple JSON of infra/wiremock/README.md, D-005), asked in
 * parallel. Each call has a time limit and one retry; a source that still gives no answer is
 * recorded as C (Q-039 A), with a detail saying whether it failed or did not answer in time
 * (MAM-20). The LEIE is asked for the provider and, for an organisation with an owner NPI, for the
 * owner as well (MAM-22).
 */
@Component
public class HttpScreeningSources implements ScreeningSources {

  private static final Logger LOG = LoggerFactory.getLogger(HttpScreeningSources.class);

  private final RestClient http;
  private final Clock clock;
  private final int retries;

  public HttpScreeningSources(
      @Value("${pem.sources-url}") String sourcesUrl,
      @Value("${pem.screening.source-timeout:2s}") Duration timeout,
      @Value("${pem.screening.source-retries:1}") int retries,
      Clock clock) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    this.http = RestClient.builder().baseUrl(sourcesUrl).requestFactory(factory).build();
    this.clock = clock;
    this.retries = retries;
  }

  @Override
  public List<SourceResult> check(ProviderFacts f) {
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<CompletableFuture<SourceResult>> calls =
          List.of(
              CompletableFuture.supplyAsync(() -> licence(f), pool),
              CompletableFuture.supplyAsync(() -> leie(f, pool), pool),
              CompletableFuture.supplyAsync(
                  () ->
                      ask(
                          Source.SAMGOV,
                          () -> get("/sam/debarments?npi={npi}&name={name}", f.npi(), nz(f.name())),
                          a -> SourceRules.sam(a.path("debarred").asBoolean(), clock.instant())),
                  pool),
              CompletableFuture.supplyAsync(
                  () ->
                      ask(
                          Source.SSADMF,
                          () -> get("/ssa-dmf/deaths?npi={npi}", f.npi()),
                          a ->
                              SourceRules.ssaDmf(
                                  a.path("deceased").asBoolean(),
                                  a.path("dateOfDeath").asText(),
                                  clock.instant())),
                  pool));
      return calls.stream().map(CompletableFuture::join).toList();
    }
  }

  /**
   * MAM-22: one lookup of the provider's own NPI and, when the provider record holds an owner NPI,
   * a second lookup of the owner, both with the usual retry. If either lookup gives no answer the
   * result is C without a code (AC-5); otherwise SourceRules decides P, E201 or E203.
   */
  private SourceResult leie(ProviderFacts f, ExecutorService pool) {
    String ownerNpi = f.ownerNpi() == null || f.ownerNpi().isBlank() ? null : f.ownerNpi();
    CompletableFuture<Answer> owner =
        ownerNpi == null
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.supplyAsync(
                () -> call(Source.OIGLEIE, () -> get("/leie/exclusions?npi={npi}", ownerNpi)),
                pool);
    Answer own = call(Source.OIGLEIE, () -> get("/leie/exclusions?npi={npi}", f.npi()));
    Answer theOwner = owner.join();
    if (!own.answered()) {
      return notAnswered(Source.OIGLEIE, own);
    }
    if (theOwner != null && !theOwner.answered()) {
      return notAnswered(Source.OIGLEIE, theOwner);
    }
    return SourceRules.leie(
        f.npi(),
        exclusion(own.body()),
        ownerNpi,
        theOwner == null ? SourceRules.Exclusion.NONE : exclusion(theOwner.body()),
        clock.instant());
  }

  private static SourceRules.Exclusion exclusion(JsonNode a) {
    return new SourceRules.Exclusion(
        a.path("excluded").asBoolean(),
        a.path("exclusionType").asText(),
        a.path("exclusionDate").asText());
  }

  private SourceResult licence(ProviderFacts f) {
    if (!f.individual()) {
      return SourceRules.licence(false, null, null, false, null, null, clock.instant());
    }
    if (f.licenseNo() == null || f.licenseNo().isBlank()) {
      return SourceRules.licence(true, null, null, false, null, null, clock.instant());
    }
    return ask(
        Source.LICBOARD,
        () ->
            get(
                "/license-board/licenses?licenseNo={no}&state={state}",
                f.licenseNo(),
                nz(f.licenseState())),
        a ->
            SourceRules.licence(
                true,
                f.licenseNo(),
                f.licenseState(),
                a.path("found").asBoolean(),
                a.path("status").asText(null),
                a.path("expiry").asText(null),
                clock.instant()));
  }

  private SourceResult ask(
      Source source, Supplier<JsonNode> call, Function<JsonNode, SourceResult> rule) {
    Answer a = call(source, call);
    return a.answered() ? rule.apply(a.body()) : notAnswered(source, a);
  }

  /** The call with its retries; no answer after the last attempt gives an empty Answer. */
  private Answer call(Source source, Supplier<JsonNode> call) {
    boolean timedOut = false;
    for (int attempt = 0; attempt <= retries; attempt++) {
      try {
        JsonNode body = call.get();
        if (body == null) {
          throw new IllegalStateException("empty answer");
        }
        return new Answer(body, false);
      } catch (RuntimeException e) {
        timedOut = isTimeout(e);
        LOG.warn("screening source {} attempt {} failed: {}", source, attempt + 1, e.toString());
      }
    }
    return new Answer(null, timedOut);
  }

  private SourceResult notAnswered(Source source, Answer a) {
    return a.timedOut()
        ? SourceRules.timedOut(source, clock.instant())
        : SourceRules.unreachable(source, clock.instant());
  }

  private static boolean isTimeout(Throwable e) {
    for (Throwable c = e; c != null; c = c.getCause()) {
      if (c instanceof HttpTimeoutException || c instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }

  private JsonNode get(String uri, Object... vars) {
    return http.get().uri(uri, vars).retrieve().body(JsonNode.class);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  /** A source's answer, or none, with whether the last attempt ran out of time. */
  private record Answer(JsonNode body, boolean timedOut) {

    boolean answered() {
      return body != null;
    }
  }
}
