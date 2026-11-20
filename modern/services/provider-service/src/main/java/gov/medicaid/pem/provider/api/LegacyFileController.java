package gov.medicaid.pem.provider.api;

import gov.medicaid.pem.provider.application.ExportProviderMaster;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Legacy-format files of provider-service (contract tag "Legacy files"). */
@RestController
@RequestMapping("/api/v1/legacy")
public class LegacyFileController {

  private final ExportProviderMaster export;

  public LegacyFileController(ExportProviderMaster export) {
    this.export = export;
  }

  /** exportProviderMaster (MAM-43): x-roles ADMIN; 130-byte PRVRECRD records, one per line. */
  @GetMapping("/provider-master")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> exportProviderMaster() {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_PLAIN)
        .body(export.export().getBytes(StandardCharsets.US_ASCII));
  }
}
