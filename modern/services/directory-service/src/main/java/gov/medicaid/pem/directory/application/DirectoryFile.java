package gov.medicaid.pem.directory.application;

import java.util.List;

/** The legacy DIROUT file (ADR-005): one 100-byte record per entry, in the order given. */
public interface DirectoryFile {

  byte[] write(List<DirectoryEntry> entries);
}
