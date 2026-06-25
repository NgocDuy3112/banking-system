from __future__ import annotations

import math
from datetime import datetime
from typing import Mapping

from app.core import constants


def is_round_amount(amount: float) -> float:
    """Return 1.0 if amount is an exact multiple of any round VND divisor.

    Round numbers (1M, 10M, 50M, 100M, 500M, 1B VND) are over-represented
    in fraud. Legitimate users rarely send amounts that line up with these
    exact divisors.
    """
    if amount <= 0:
        return 0.0
    for divisor in constants.ROUND_AMOUNT_DIVISORS:
        if amount % divisor == 0:
            return 1.0
    return 0.0


def round_amount_log(amount: float) -> float:
    """log10 of the smallest round divisor that divides amount evenly.

    0 if amount isn't a round number. Otherwise: log10(divisor) so e.g.
    a 10M VND amount maps to ~7.0, 100M to ~8.0, 1B to ~9.0. Captures
    magnitude of the roundness pattern in a continuous signal.
    """
    if amount <= 0:
        return 0.0
    smallest = None
    for divisor in constants.ROUND_AMOUNT_DIVISORS:
        if amount % divisor == 0:
            smallest = divisor
            break
    if smallest is None:
        return 0.0
    return math.log10(smallest)


def amount_to_balance_pct(amount: float, from_balance: float) -> float:
    """(amount / from_balance) * 100. Returns 0.0 when from_balance is 0.

    Distinct from `balance_emptying_ratio` (which is unbounded 0–1+).
    Pct makes it easier for trees to find clean splits.
    """
    if from_balance <= 0:
        return 0.0
    return (amount / from_balance) * 100.0


def is_total_drain(amount: float, from_balance: float) -> float:
    """1.0 if amount >= 99% of from_balance (account drained to nothing).

    Stricter than `balance_emptying_ratio` (>0.9). Catches the "wipe
    the account" pattern: amount = balance, often the exact balance.
    """
    if from_balance <= 0:
        return 0.0
    ratio = amount / from_balance
    return 1.0 if ratio >= constants.TOTAL_DRAIN_THRESHOLD else 0.0


# === v4: amount tier features (Group A) ===
# PaySim's fraud distribution is heavily concentrated in 1M-100M VND
# (99.4x over-representation in the medium tier). 96.6% of legit traffic
# is <1M (micro). These binary tiers let the model exploit the gap without
# making assumptions about specific currency thresholds.

def amount_tier_micro(amount: float) -> float:
    """1.0 if amount < 1M VND. ~96.6% of legit traffic, ~67% of fraud."""
    return 1.0 if amount < 1_000_000 else 0.0


def amount_tier_small(amount: float) -> float:
    """1.0 if 1M <= amount < 10M VND. ~3% of legit, ~27% of fraud (~8x)."""
    return 1.0 if 1_000_000 <= amount < 10_000_000 else 0.0


def amount_tier_medium(amount: float) -> float:
    """1.0 if 10M <= amount < 100M VND. ~0.1% of legit, ~6% of fraud (~99x)."""
    return 1.0 if 10_000_000 <= amount < 100_000_000 else 0.0


# === v4: interaction features (Group D) ===
# Combinations of (is_transfer, amount tier, balance_emptying_ratio) that
# the model would otherwise have to learn via deep trees.

def is_transfer_and_draining(is_transfer: float, balance_emptying_ratio: float) -> float:
    """1.0 if INTERBANK transfer + balance_emptying_ratio > 0.9.

    The canonical fraud pattern in PaySim: external transfer that empties
    the account.
    """
    return 1.0 if (is_transfer > 0 and balance_emptying_ratio > 0.9) else 0.0


def is_medium_and_draining(amount: float, from_balance: float) -> float:
    """1.0 if amount in 10M-100M range AND amount > 99% of balance.

    The highest-signal combo: medium-tier amount (already 99x over fraud)
    that also empties the account.
    """
    if from_balance <= 0:
        return 0.0
    ratio = amount / from_balance
    return 1.0 if (10_000_000 <= amount < 100_000_000 and ratio > 0.99) else 0.0


def post_balance_ratio(amount: float, from_balance: float) -> float:
    """DEPRECATED: dropped from v3 due to PaySim simulator leak.

    Originally `(from_balance - amount) / from_balance` — fraction of balance
    remaining after the transaction. In PaySim, fraudsters leave
    `newbalanceOrig == 0` 100% of the time, so this feature gave the model
    a perfect discriminator that does NOT generalize to real banking data.

    Kept here only because `tests/test_shap.py` imports it; new code must
    not call it.
    """
    if from_balance <= 0:
        return 0.0
    return (from_balance - amount) / from_balance


def is_night(hour: int) -> float:
    """1.0 if hour is in [0, 5]. Night-time transactions are riskier."""
    return 1.0 if constants.NIGHT_HOUR_START <= hour <= constants.NIGHT_HOUR_END else 0.0


def is_office_hours(hour: int) -> float:
    """1.0 if hour is in [9, 17]. Office-hours transactions are safer."""
    return 1.0 if constants.OFFICE_HOUR_START <= hour <= constants.OFFICE_HOUR_END else 0.0


def is_weekend(dow: int) -> float:
    """1.0 if day-of-week is Sat (5) or Sun (6)."""
    return 1.0 if dow >= constants.WEEKEND_DAY_START else 0.0


def sin_cos_hour(hour: int) -> tuple[float, float]:
    """Cyclical encoding of hour-of-day. Hour 23 and hour 0 are neighbors."""
    angle = 2 * math.pi * hour / constants.HOURS_IN_DAY
    return math.sin(angle), math.cos(angle)


def sin_cos_dow(dow: int) -> tuple[float, float]:
    """Cyclical encoding of day-of-week (Mon=0..Sun=6). Sun wraps to Mon."""
    angle = 2 * math.pi * dow / constants.DAYS_IN_WEEK
    return math.sin(angle), math.cos(angle)


def compute_request_features(
    amount: float,
    from_balance: float,
    transaction_type: str,
    occurred_at: datetime,
) -> dict[str, float]:
    """Compute ALL request-time features (no DB lookups).

    Used by inference (model.py) and as the reference implementation for
    notebook 03's feature engineering functions. Returns a dict keyed by
    feature name so the caller can assemble the model's input vector.
    """
    sin_h, cos_h = sin_cos_hour(occurred_at.hour)
    sin_d, cos_d = sin_cos_dow(occurred_at.weekday())

    is_transfer = 1.0 if transaction_type == "INTERBANK" else 0.0
    balance_emptying_ratio = (amount / from_balance) if from_balance > 0 else 0.0

    return {
        # --- type + amount ---
        "is_transfer": is_transfer,
        "amount_log": math.log1p(amount),
        "balance_emptying_ratio": balance_emptying_ratio,
        # --- round-number features ---
        "is_round_amount": is_round_amount(amount),
        "round_amount_log": round_amount_log(amount),
        # --- amount-balance derivatives ---
        "amount_to_balance_pct": amount_to_balance_pct(amount, from_balance),
        "is_total_drain": is_total_drain(amount, from_balance),
        # NOTE: "post_balance_ratio" and "newbalanceOrig" intentionally dropped
        # in v3 — they encoded PaySim simulator artifacts (fraud → balance=0)
        # that do NOT generalize to real banking data. See docs/mlops.md.
        # --- v4 amount-tier features (Group A) ---
        # Binary indicators of VND amount tier. 99.4x over-representation
        # in medium tier drives the strongest fraud signal in PaySim.
        "amount_tier_micro": amount_tier_micro(amount),
        "amount_tier_small": amount_tier_small(amount),
        "amount_tier_medium": amount_tier_medium(amount),
        # --- v4 interaction features (Group D) ---
        # Combinations of type + amount tier + balance emptying that
        # the model would otherwise have to learn via deep trees.
        "is_transfer_and_draining": is_transfer_and_draining(is_transfer, balance_emptying_ratio),
        "is_medium_and_draining": is_medium_and_draining(amount, from_balance),
        # --- time cyclical ---
        "sin_hour": sin_h,
        "cos_hour": cos_h,
        "sin_dow": sin_d,
        "cos_dow": cos_d,
        # --- time buckets ---
        "is_night": is_night(occurred_at.hour),
        "is_office_hours": is_office_hours(occurred_at.hour),
        "is_weekend": is_weekend(occurred_at.weekday()),
    }
