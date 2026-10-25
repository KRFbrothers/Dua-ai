"""Bounded retries for classified read failures only; never replay writes/timeouts."""
import time

READ_TOOLS = frozenset(('fetch_url', 'list_files', 'read_file', 'search_code', 'search_notes',
                        'list_issues', 'read_issue', 'search_issues', 'check_pr_ci', 'workspace_status', 'workspace_diff', 'list_workspaces'))
TRANSIENT = frozenset((429, 500, 502, 503, 504))


def transient(error):
    code = getattr(error, 'status_code', None) or getattr(error, 'code', None)
    return isinstance(code, int) and code in TRANSIENT


def retry_read(name, invoke, cancelled=None, wait=time.sleep, observer=None):
    for attempt in range(2):
        if cancelled and cancelled():
            from agent_tools import Cancelled
            raise Cancelled()
        result = invoke()
        if name not in READ_TOOLS or attempt or not isinstance(result, dict) or result.get('retryable') is not True:
            return result
        if observer:
            observer.emit('tool.retry', tool=name, attempt=2)
        # Short bounded backoff, cancellable before each slice and the next call.
        for _ in range(5):
            if cancelled and cancelled():
                from agent_tools import Cancelled
                raise Cancelled()
            wait(0.1)
    return result
