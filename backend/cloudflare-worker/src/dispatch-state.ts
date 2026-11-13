/**
 * Decide whether an idempotency record must suppress a new push attempt.
 * Unknown/legacy states are suppressed conservatively to avoid duplicate pushes.
 */
export function shouldSuppressDispatch(status: unknown, leaseUntilMillis: number, nowMillis: number): boolean {
  if (status === "sent") return true;
  if (status === "failed") return false;
  if (status === "pending") return Number.isFinite(leaseUntilMillis) && leaseUntilMillis > nowMillis;
  return true;
}
