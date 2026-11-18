import sys
from pathlib import Path

KAG = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(KAG / "loaders"))

REPO = KAG.parents[1]
SRC = REPO / "source-code"
AWS = REPO / "aws-transform"


def read(rel: str) -> str:
    """Read a legacy source file (paths relative to source-code/). Never modifies it."""
    return (SRC / rel).read_text(encoding="utf-8", errors="replace")
