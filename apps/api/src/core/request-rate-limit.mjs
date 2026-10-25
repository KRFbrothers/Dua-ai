/** Per-process burst protection. Durable daily quotas remain the cross-instance cap. */
export class RequestRateLimiter {
  constructor({ clock = () => performance.now(), maxAccounts = 10000 } = {}) {
    this.clock = clock;
    this.maxAccounts = maxAccounts;
    this.accounts = new Map();
  }

  consume(account, limit) {
    const now = this.clock();
    // A full bucket can be discarded without granting extra requests.
    for (const [key, value] of this.accounts) {
      if (now - value.updated >= 60000) this.accounts.delete(key);
    }
    let bucket = this.accounts.get(account);
    if (!bucket) {
      if (this.accounts.size >= this.maxAccounts) return { allowed: false, retryAfterSeconds: 60 };
      bucket = { tokens: limit, updated: now, limit };
      this.accounts.set(account, bucket);
    }
    bucket.tokens = Math.min(limit, bucket.tokens + Math.max(0, now - bucket.updated) * bucket.limit / 60000);
    bucket.updated = now;
    bucket.limit = limit;
    if (bucket.tokens < 1) {
      return { allowed: false, retryAfterSeconds: Math.max(1, Math.ceil((1 - bucket.tokens) * 60 / limit)) };
    }
    bucket.tokens -= 1;
    return { allowed: true };
  }
}

export const chatRateLimiter = new RequestRateLimiter();
