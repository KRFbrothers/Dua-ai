"""Output secret filtering shared by buffered and incremental ADK answers.

Authorization/tool allowlists remain enforced in server code. This is not a claim
that a text filter can prove arbitrary model advice correct or safe.
"""
import os
import re

KEY_NAMES = ('OPENAI_API_KEY','ANTHROPIC_API_KEY','OPENROUTER_API_KEY','GITHUB_TOKEN',
             'SUPABASE_SERVICE_ROLE_KEY','DUA_APP_TOKEN','ADK_SESSION_DB_URL')
PATTERNS = (
    re.compile(r'\b(?:sk-|ghp_|github_pat_|AIza)[A-Za-z0-9_-]{12,}'),
    re.compile(r'-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----',re.S),
)


class Redactor:
    def __init__(self, secrets=None):
        self.secrets = sorted({v for v in (secrets if secrets is not None else
            [os.environ.get(k,'') for k in KEY_NAMES]) if isinstance(v,str) and len(v)>=8}, key=len, reverse=True)
        self.buffer = ''

    def clean(self,text):
        for value in self.secrets:
            text=text.replace(value,'[redacted secret]')
        for pattern in PATTERNS:
            text=pattern.sub('[redacted secret]',text)
        return text

    def feed(self,delta='',final=False):
        self.buffer += delta
        if final:
            result=self.clean(self.buffer)
            self.buffer=''
            return result
        cut=len(self.buffer)
        # Hold only a suffix that could become a key, not every ordinary word.
        # This keeps short safe answer deltas live while protecting split keys.
        prefixes=self.secrets+['sk-','ghp_','github_pat_','AIza','-----BEGIN ']
        for value in prefixes:
            for size in range(1,min(len(value),len(self.buffer))+1):
                if self.buffer.endswith(value[:size]):cut=min(cut,len(self.buffer)-size)
        unfinished=re.search(r'\b(?:sk-|ghp_|github_pat_|AIza)[A-Za-z0-9_-]*$',self.buffer)
        if unfinished:cut=min(cut,unfinished.start())
        begin=self.buffer.find('-----BEGIN ')
        if begin>=0 and '-----END ' not in self.buffer[begin:]:cut=min(cut,begin)
        for value in self.secrets:
            start=self.buffer.find(value)
            if start>=0 and start<cut<start+len(value):cut=start
        for pattern in PATTERNS:
            for match in pattern.finditer(self.buffer):
                if match.start()<cut<match.end():cut=match.start()
        prefix,self.buffer=self.buffer[:cut],self.buffer[cut:]
        return self.clean(prefix)


def filter_text(text):
    return Redactor().clean(text)
