package gov.medicaid.pem.provider.infrastructure;

import gov.medicaid.pem.provider.application.ProviderChanges;
import gov.medicaid.pem.provider.application.ProviderSearch;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Field changes (the optimistic-lock version rises by one per change) and the staff search on
 * provider_db.
 */
@Component
public class JpaProviderChanges
    implements ProviderChanges,
        ProviderSearch,
        gov.medicaid.pem.provider.application.DecisionWriter {

  @Override
  public void save(gov.medicaid.pem.provider.domain.Provider decided) {
    boolean found =
        change(
            decided.npi(),
            e -> {
              e.enrollStatus = decided.enrollStatus().code();
              e.revalDueDate = decided.revalidationDueDate();
              e.endDate = decided.endDate();
            });
    if (!found) {
      throw new IllegalStateException(
          "provider " + decided.npi() + " disappeared while being decided");
    }
  }

  private final ProviderJpaRepository repo;
  private final Clock clock;

  public JpaProviderChanges(ProviderJpaRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  @Override
  public boolean setScreenFlag(String npi, ScreenFlag flag) {
    return change(npi, e -> e.screenFlag = flag.code());
  }

  @Override
  public boolean setRiskTier(String npi, RiskTier tier) {
    return change(npi, e -> e.riskTier = tier.code());
  }

  private boolean change(String npi, Consumer<ProviderEntity> edit) {
    return repo.findByNpi(npi)
        .map(
            e -> {
              edit.accept(e);
              e.updatedAt = clock.instant();
              repo.saveAndFlush(e);
              return true;
            })
        .orElse(false);
  }

  @Override
  public Page find(Criteria c, int page, int size) {
    var result = repo.findAll(matching(c), PageRequest.of(page, size, Sort.by("npi")));
    return new Page(
        result.getContent().stream().map(JpaProviderStore::toDomain).toList(),
        page,
        size,
        result.getTotalElements());
  }

  /**
   * MAM-37 AC-9: the key goes into the item query only (npi &gt; key, ORDER BY npi, LIMIT size, on
   * the unique index of npi); the count uses the filters alone, so total stays "all matches".
   */
  @Override
  public Page findAfter(Criteria c, String afterNpi, int size) {
    Specification<ProviderEntity> matches = matching(c);
    List<ProviderEntity> items =
        repo.findBy(
            matches.and((root, query, cb) -> cb.greaterThan(root.<String>get("npi"), afterNpi)),
            q -> q.sortBy(Sort.by("npi")).limit(size).all());
    return new Page(
        items.stream().map(JpaProviderStore::toDomain).toList(), 0, size, repo.count(matches));
  }

  /** MAM-37: every given filter must match; the name filter is a case-blind "contains". */
  static Specification<ProviderEntity> matching(Criteria c) {
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      if (c.npi() != null) {
        where.add(cb.equal(root.get("npi"), c.npi()));
      }
      if (c.medicaidId() != null) {
        where.add(cb.equal(cb.trim(root.<String>get("medicaidId")), c.medicaidId().trim()));
      }
      if (c.status() != null) {
        where.add(cb.equal(root.get("enrollStatus"), c.status().code()));
      }
      if (c.screenFlag() != null) {
        where.add(cb.equal(root.get("screenFlag"), c.screenFlag().code()));
      }
      if (c.name() != null && !c.name().isBlank()) {
        String like = "%" + escape(c.name().trim().toUpperCase(Locale.ROOT)) + "%";
        where.add(
            cb.or(
                Stream.of("lastName", "firstName", "legalName", "dbaName")
                    .map(f -> cb.like(cb.upper(root.<String>get(f)), like, '\\'))
                    .toArray(Predicate[]::new)));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
