#!/usr/bin/env python3
"""Report resumable-study progress and an observed-throughput ETA."""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timedelta
import json
from pathlib import Path
import time


def format_duration(seconds: float | None) -> str | None:
    if seconds is None:
        return None
    return str(timedelta(seconds=round(seconds)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--total", type=int, required=True)
    args = parser.parse_args()
    start = (args.root / "study.tsv").stat().st_mtime
    complete_files = list(args.root.glob("results/**/.complete"))
    failed_files = list(args.root.glob("results/**/.failed"))
    prior = sum(path.stat().st_mtime < start for path in complete_files)
    new = len(complete_files) - prior
    elapsed = max(time.time() - start, 1)
    rate_per_hour = new / elapsed * 3600 if new else 0.0
    remaining = args.total - len(complete_files)
    eta_seconds = remaining / (rate_per_hour / 3600) if rate_per_hour else None
    phases = Counter()
    for path in complete_files:
        relative = path.relative_to(args.root / "results")
        phases[f"{relative.parts[0]}/{relative.parts[1]}"] += 1
    result = {
        "observed_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "completed": len(complete_files), "failed": len(failed_files), "total": args.total,
        "percent": round(len(complete_files) / args.total * 100, 2),
        "resumed_completed": prior, "new_completed": new,
        "elapsed_since_resume": format_duration(elapsed),
        "throughput_runs_per_hour": round(rate_per_hour, 1),
        "eta_duration": format_duration(eta_seconds),
        "estimated_finish": (datetime.now().astimezone() + timedelta(seconds=eta_seconds)).isoformat(timespec="minutes")
                            if eta_seconds is not None else None,
        "completed_by_experiment": dict(sorted(phases.items())),
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
