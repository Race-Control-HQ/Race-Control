"""
Offline tests for `fastf1_service._clean`, specifically the fallback that
treats a pre-stringified "nan"/"NaT"/"None" as a real missing value.

Regression: a qualifying-results row with no recorded driver number arrived
from an upstream FastF1/Ergast column as the literal string "nan" (not an
actual float NaN, which `_clean` already handled). Left uncleaned, that
string flowed straight into the `/api/results` response as `driverNumber:
"nan"`, and on the web client every such row shared the same React list key
(`driverId ?? driverNumber` -> "nan"), producing a "two children with the
same key" warning and, worse, duplicated/omitted rows in the Qualifying tab.

    python test_clean_helper.py     (or: pytest test_clean_helper.py)
"""

import math

import pandas as pd

import fastf1_service as svc


def test_actual_nan_float_is_cleaned():
    assert svc._clean(float("nan")) is None


def test_actual_nat_is_cleaned():
    assert svc._clean(pd.NaT) is None


def test_stringified_nan_is_cleaned():
    assert svc._clean("nan") is None
    assert svc._clean("NaN") is None
    assert svc._clean("  NaN  ") is None


def test_stringified_nat_and_none_are_cleaned():
    assert svc._clean("NaT") is None
    assert svc._clean("None") is None
    assert svc._clean("<NA>") is None


def test_real_string_values_pass_through():
    assert svc._clean("44") == "44"
    assert svc._clean("VER") == "VER"
    # A team/driver name that merely contains "nan" as a substring must not
    # be treated as a missing value.
    assert svc._clean("Fernando") == "Fernando"


def test_numpy_nan_scalar_is_still_cleaned():
    import numpy as np

    assert svc._clean(np.float64("nan")) is None
    assert math.isnan(float("nan"))  # sanity check the test's own assumption


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-v"]))
