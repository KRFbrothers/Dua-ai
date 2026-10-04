// Per-instance burst guard; independent instances/cold starts have separate windows.
export function createBurstGuard(now = Date.now) {
  const buckets = new Map();
  return (request, group, limit) => {
    const time = now();
    for (const [key, value] of buckets) if (value.until <= time) buckets.delete(key);
    // Vercel overwrites x-forwarded-for at its edge.
    const key = group + ':' + (request.headers.get('x-forwarded-for') || 'unknown').slice(0,200);
    let bucket = buckets.get(key);
    if (!bucket) {
      if (buckets.size >= 4096) return 60;
      bucket = { count: 0, until: time + 60000 }; buckets.set(key,bucket);
    }
    bucket.count++;
    return bucket.count > limit ? Math.max(1,Math.ceil((bucket.until-time)/1000)) : 0;
  };
}
