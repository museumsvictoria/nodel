# Jython 2.5 vs 2.7 Benchmark Deep Dive

## Data sources
- Jython 2.5 results: `build/benchmarks/jython-2.5-rerun-20260130_131700/`
- Jython 2.7 results: `build/benchmarks/jython-2.7-rerun-20260130_131729/`
- Jython 2.7 ASCII folder test: `build/benchmarks/jython-2.7-ascii-20260130_131759/`
- Jython 2.5 single-node test: `build/benchmarks/jython-2.5-single-calendar-20260130_131830/`
- Jython 2.7 single-node test: `build/benchmarks/jython-2.7-single-calendar-20260130_131859/`
- Nodes dataset: `benchmark_nodes/`

## Environment
- Java: OpenJDK Temurin 25.0.1+8 LTS

## Dataset sanity
- Loaded nodes are the top-level folders: Calendar, Global Caché iTach IP2SL, NEC Display, PJLink, Wake-on-LAN.
- Nested folders (`NEC Display/Mk1`, `PJLink/pjlink`) are not loaded because node scanning is non-recursive.
- The Global Caché folder name includes a non-ASCII character (e-acute).

## Key numbers (means)

| Metric | Jython 2.5 | Jython 2.7 | Factor |
| --- | --- | --- | --- |
| Node load time (avg, all nodes) | 524.96 ms | 1446.24 ms | ~2.76x |
| HTTP ready time | 432.25 ms | 632.94 ms | ~1.46x |
| Time to last node | 2953.00 ms | 4005.63 ms | ~1.36x |
| Interpreter init time | 0.36 ms | 1047.40 ms | ~2900x |

## Per-node load times (mean)

| Node | Jython 2.5 | Jython 2.7 | Factor |
| --- | --- | --- | --- |
| Calendar | 487.00 ms | 1335.00 ms | ~2.74x |
| Global Caché iTach IP2SL | 614.60 ms | 1307.40 ms | ~2.13x |
| NEC Display | 449.80 ms | 1331.00 ms | ~2.96x |
| PJLink | 652.80 ms | 1627.20 ms | ~2.49x |
| Wake-on-LAN | 420.60 ms | 1630.60 ms | ~3.88x |

## Root cause signal

Interpreter initialization dominates the regression:
- Jython 2.5 interpreter init is effectively zero (0-4 ms).
- Jython 2.7 interpreter init is ~509-1101 ms per node.
- The load minus init time is ~0.4s on Jython 2.7 vs ~0.5s on Jython 2.5, so the slowdown is almost entirely interpreter startup.

## Compatibility issue observed (2.7 only)

Baseline 2.7 runs show:
- `ImportError: No module named nodetoolkit` for the Global Cache iTach IP2SL node.
- This node is the only one with a non-ASCII folder name (e-acute), suggesting a path encoding issue in Jython 2.7.

ASCII folder test (2.7) shows:
- No `nodetoolkit` import errors when the node folder name is ASCII.
- Performance is unchanged or slightly slower; the encoding fix does not address the runtime regression.

## Single-node test (Calendar only)

| Metric | Jython 2.5 | Jython 2.7 | Factor |
| --- | --- | --- | --- |
| Node load time | 301.80 ms | 813.80 ms | ~2.70x |
| HTTP ready time | 564.95 ms | 630.38 ms | ~1.12x |
| Time to last node | 2594.96 ms | 3149.73 ms | ~1.21x |
| Interpreter init time | 0.20 ms | 763.00 ms | ~3800x |

## Jar size check

- `benchmark_jars/nodelhost-2.5.jar` ~19 MB
- `benchmark_jars/nodelhost-2.7.jar` ~58 MB

The larger Jython 2.7 artifact likely contributes to the increased startup and per-node interpreter initialization time.
