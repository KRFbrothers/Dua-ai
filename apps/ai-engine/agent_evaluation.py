"""Versioned, deterministic answer/tool quality scoring; no judge/model calls.

Responses are collected separately from the chosen deployment/model. Missing or
repeated cases fail rather than inflating an apparent accuracy score. Offline
fixtures test the evaluator, not the quality of a live model.
"""
import json
import re


def evaluate(cases, responses):
    if not isinstance(cases, dict) or cases.get('version') != 1 or not isinstance(cases.get('cases'),list):
        raise ValueError('Unsupported evaluation dataset')
    expected=cases['cases']
    ids=[c.get('id') for c in expected]
    if not ids or len(set(ids))!=len(ids) or any(not isinstance(i,str) for i in ids):
        raise ValueError('Evaluation cases require unique string IDs')
    answers={}
    for response in responses:
        if not isinstance(response,dict) or response.get('id') not in ids or response['id'] in answers:
            raise ValueError('Duplicate or unknown response ID')
        if not isinstance(response.get('text'),str) or not isinstance(response.get('tools',[]),list):
            raise ValueError('Responses need text and a tool-name list')
        answers[response['id']]=response
    results=[]
    for case in expected:
        response=answers.get(case['id'])
        if response is None:
            results.append({'id':case['id'],'passed':False,'checks':{'response_present':False}})
            continue
        text=response['text']
        lower=text.lower()
        rubric=case.get('rubric',{})
        checks={'response_present':True}
        for token in rubric.get('contains',[]):checks['contains:'+token]=token.lower() in lower
        for token in rubric.get('forbidden',[]):checks['excludes:'+token]=token.lower() not in lower
        if 'number' in rubric:
            expected_number=float(rubric['number'])
            found=[float(n) for n in re.findall(r'(?<![\w.])-?\d+(?:\.\d+)?',text)]
            checks['numeric_answer']=any(abs(n-expected_number)<1e-6 for n in found)
        if rubric.get('citation_required'):
            checks['cited']=bool(re.search(r'\[(?:\d+|D:[a-f0-9-]+:\d+)\]',text))
        tools=response.get('tools',[])
        for tool in rubric.get('required_tools',[]):checks['used:'+tool]=tool in tools
        for tool in rubric.get('forbidden_tools',[]):checks['avoided:'+tool]=tool not in tools
        if 'approval_required' in rubric:
            checks['approval_state']=response.get('approval_required') is rubric['approval_required']
        results.append({'id':case['id'],'passed':all(checks.values()),'checks':checks})
    passed=sum(r['passed'] for r in results)
    return {'datasetVersion':1,'cases':len(results),'responses':len(answers),'passed':passed,
            'passRate':passed/len(results),'results':results,
            'method':'deterministic_reference_rubrics','liveModelAccuracyClaim':False}


def load_responses(path):
    with open(path) as file:
        return [json.loads(line) for line in file if line.strip()]
