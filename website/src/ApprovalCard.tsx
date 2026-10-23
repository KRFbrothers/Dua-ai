import { useEffect, useRef, useState } from 'react';
import { ApiError, decideAction, type Approval } from './api';

type Status = 'pending' | 'sending' | 'done' | 'rejected' | 'failed' | 'expired' | 'unknown' | 'unavailable';
const labels: Record<Status, string> = {
  pending: 'Approval needed', sending: 'Submitting decision…', done: 'Completed', rejected: 'Rejected',
  failed: 'Action failed', expired: 'Approval expired', unknown: 'Result unknown', unavailable: 'Approval unavailable'
};

export default function ApprovalCard({ action, disabled }: { action: Approval; disabled: boolean }) {
  const [status, setStatus] = useState<Status>('pending');
  const [text, setText] = useState('');
  const [now, setNow] = useState(Date.now());
  const locked = useRef(false);
  const expired = now >= action.expiresAt;
  useEffect(() => {
    if (status !== 'pending' || expired) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [status, expired]);

  async function decide(decision: 'approve' | 'reject') {
    if (locked.current || disabled || Date.now() >= action.expiresAt || status !== 'pending') return;
    locked.current = true;
    setStatus('sending');
    try {
      const result = await decideAction(action.id, decision);
      setStatus(result.status);
      setText(result.text);
    } catch (error) {
      const code = error instanceof ApiError ? error.status : 502;
      setStatus(code === 410 ? 'expired' : code >= 500 ? 'unknown' : 'unavailable');
      setText(error instanceof Error ? error.message : 'Check the destination before asking the agent again.');
    }
    // Never unlock an attempted decision: the single-use action may already have executed.
  }
  const shown = status === 'pending' && expired ? 'expired' : status;
  return (
    <section className="action-card" aria-label={action.title}>
      <div className="action-status" role="status">{labels[shown]}</div>
      <strong>{action.title}</strong>
      <ul>{action.details.map((line, index) => <li key={index}>{line}</li>)}</ul>
      {action.diff && <details><summary>Review proposed changes</summary><pre><code>{action.diff}</code></pre>
        {action.diffTruncated && <p className="muted small">This preview is shortened. Review the full changes before approving.</p>}
      </details>}
      {shown === 'pending' ? <>
        <p className="muted small">Nothing has run yet. Expires in {Math.max(1, Math.ceil((action.expiresAt - now) / 60000))} min.</p>
        <div className="row">
          <button type="button" className="pill" disabled={disabled} onClick={() => decide('reject')}>Reject</button>
          <button type="button" className="pill action-approve" disabled={disabled} onClick={() => decide('approve')}>Approve</button>
        </div>
      </> : <p className="action-result">{text
        ? text.split(/(https:\/\/github\.com\/[^\s<>]+)/g).map((part, i) => part.startsWith('https://github.com/')
          ? <a key={i} href={part} target="_blank" rel="noopener noreferrer">{part}</a> : part)
        : shown === 'expired' ? 'Ask the agent for a fresh proposal.' : shown === 'sending' ? 'Waiting for the result. Do not submit again.' : ''}</p>}
    </section>
  );
}
