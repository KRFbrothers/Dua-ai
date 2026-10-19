import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture(autouse=True)
def fresh_daily_budget():
    """Each test starts with empty daily counters (cost_guard keeps them in memory per process)."""
    import cost_guard
    cost_guard.BUDGET.reset()
    yield
    cost_guard.BUDGET.reset()
