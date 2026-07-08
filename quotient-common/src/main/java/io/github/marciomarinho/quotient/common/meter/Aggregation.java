package io.github.marciomarinho.quotient.common.meter;

/**
 * How a meter combines the quantities of the usage events that fall in one window.
 *
 * <p>The aggregator applies exactly one of these per (tenant, meter). Kept as a small closed enum
 * so adding an aggregation is a deliberate, reviewed change with a matching implementation in the
 * aggregator.
 */
public enum Aggregation {
  /** Add the quantities (e.g. total input tokens). */
  SUM,
  /** Count the events, ignoring quantity (e.g. number of requests). */
  COUNT,
  /** Take the largest quantity seen (e.g. peak concurrent sessions). */
  MAX
}
