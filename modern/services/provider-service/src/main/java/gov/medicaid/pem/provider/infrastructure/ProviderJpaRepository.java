package gov.medicaid.pem.provider.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data repository of the provider table. */
public interface ProviderJpaRepository
    extends JpaRepository<ProviderEntity, Long>, JpaSpecificationExecutor<ProviderEntity> {

  Optional<ProviderEntity> findByNpi(String npi);
}
