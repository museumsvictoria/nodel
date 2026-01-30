# Jython 2.7 Compatibility Plan

## Goals
- Understand and mitigate breaking changes from dictionary behavior differences.
- Benchmark load and compile times between Jython 2.5.4-rc1 and 2.7.x on the same node set.
- Produce actionable guidance for recipe authors and a decision on default behavior.

## Scope: Dictionary Behavior Differences
Concern: existing recipes may rely on `myDict['missingKey']` returning `None` (or a Java Map
default) rather than raising `KeyError` under Jython 2.7.

### Plan
1) Reproduce and characterize the behavior
   - Write a minimal test script that exercises:
     - Native Python dicts.
     - Java `Map` objects surfaced to Jython (common in Nodel APIs).
     - Any Nodel-specific dict wrappers returned by the toolkit.
   - Run under Jython 2.5.4-rc1 and 2.7.x to confirm where behavior differs.

2) Assess impact on existing recipes
   - Run a targeted scan in `recipes/` and `nodes/` for `[...]` indexing of dict-like objects.
   - Add temporary runtime logging for `KeyError` in node script execution to capture real
     offenders when running a representative recipe set.

3) Define a compatibility strategy (choose one)
   - Option A: Compatibility mode (default off) that restores 2.5-style behavior for Java Maps
     by wrapping them with a dict-like adapter that returns `None` for missing keys.
   - Option B: Keep 2.7 behavior and provide migration guidance plus runtime warnings when
     missing keys are accessed.
   - Option C: Hybrid approach: only wrap specific Nodel-provided maps where compatibility is
     most needed.

4) Document and communicate
   - Update recipe guidance to prefer `myDict.get('key')` for optional keys.
   - Add a migration note in release docs describing the behavior change and mitigation option.

## Scope: Jython 2.5 vs 2.7 Load/Compile Benchmark
Concern: load and compile time changes when upgrading Jython.

### Plan
1) Define the benchmark dataset
   - Use a representative set of nodes (e.g., standard recipes plus a handful of heavier nodes).
   - Fix node count, configuration, and file layout for repeatability.

2) Define metrics and methodology
   - Metrics:
     - Time to "host ready" from process start.
     - Per-node script compile time.
     - Total time to all nodes running.
   - Methodology:
     - Run 5-10 iterations per Jython version.
     - Same machine, JVM version, and startup flags.
     - Clear relevant caches between runs (document what is cleared).

3) Instrumentation and tooling
   - Add lightweight timing logs around:
     - Node script parse/compile in `PyNode`.
     - Node start lifecycle milestones.
   - Add a script to run and summarize benchmarks:
     - Start host, wait for ready, parse logs, capture timings.
     - Output a CSV/JSON summary for easy comparison.

4) Report results
   - Document results in a short report with averages and variance.
   - Highlight any regressions or improvements and whether they are material.

## Deliverables
- Repro test notes and a decision on dictionary compatibility approach.
- Benchmark harness + results summary.
- Migration guidance for recipe authors.
