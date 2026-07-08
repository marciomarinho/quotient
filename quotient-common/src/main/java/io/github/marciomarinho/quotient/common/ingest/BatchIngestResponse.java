package io.github.marciomarinho.quotient.common.ingest;

import java.util.List;

/**
 * The result of a batch ingestion call (HTTP 202): the per-event outcomes plus roll-up counts for
 * convenience.
 */
public record BatchIngestResponse(
    int received, int accepted, int deduplicated, List<IngestAcceptance> results) {

  public BatchIngestResponse {
    results = List.copyOf(results);
  }

  /** Build from the per-event results, deriving the counts. */
  public static BatchIngestResponse of(List<IngestAcceptance> results) {
    int dedup = (int) results.stream().filter(IngestAcceptance::deduplicated).count();
    return new BatchIngestResponse(results.size(), results.size() - dedup, dedup, results);
  }
}
