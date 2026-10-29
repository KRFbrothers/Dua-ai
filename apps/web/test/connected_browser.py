"""Exercise the real React client and SDK adapter against controlled HTTP fixtures."""
import json
import re
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

OUTPUT = Path('/workspace/dua-html-validation/connected-browser')
OUTPUT.mkdir(parents=True, exist_ok=True)
OWNER = '11111111-1111-4111-8111-111111111111'
TOKEN = 'test-token-' * 6
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path='/usr/bin/chromium', args=['--no-sandbox', '--disable-dev-shm-usage'])
    context = browser.new_context(viewport={'width': 390, 'height': 844})
    page = context.new_page()
    errors = []
    page.on('pageerror', lambda error: errors.append(str(error)))

    def mock_sdk_entry(route):
        original = route.fetch().text()
        # Vite adds HMR timestamps to imports after edits. Install the adapter in
        # the same API module instance used by App, including its current query.
        app_url = re.search(r'from "([^"]*/src/App\.tsx[^\"]*)"', original).group(1)
        app_source = route.fetch(url='http://127.0.0.1:4175' + app_url).text()
        api_url = re.search(r'from "(/src/api\.ts[^\"]*)"', app_source).group(1)
        shim = '''
          import { installConnection } from '/src/api.ts';
          import { createAppDeployConnection } from '/src/appdeploy-connection.ts';
          async function invoke(method, path, data) {
            const response = await fetch(path, {method, headers: {'Content-Type':'application/json'}, ...(data ? {body:JSON.stringify(data)} : {})});
            const body = await response.json();
            if (!response.ok) throw {response:{status:response.status,data:body}};
            return {data:body};
          }
          installConnection(createAppDeployConnection({get:path=>invoke('GET',path),post:(path,data)=>invoke('POST',path,data)}));
        '''
        route.fulfill(body=shim.replace("'/src/api.ts'", json.dumps(api_url)) + original, content_type='application/javascript')

    def backend(route):
        operation = route.request.url.rsplit('/', 1)[-1]
        body = route.request.post_data_json or {}
        status = 200
        if operation == 'status':
            result = {'connected': True, 'configured': True}
        elif operation == 'login':
            if body.get('email') != 'reader@example.invalid':
                status = 403
                result = {'error': 'Only the configured personal account can sign in.'}
            else:
                result = {'configured': True, 'user': {'id': OWNER, 'email': 'reader@example.invalid', 'displayName': 'Reader Example', 'avatarUrl': ''},
                          'session': {'accessToken': TOKEN, 'refreshToken': 'test-refresh-' * 6, 'expiresAt': 4102444800}}
        elif operation == 'logout':
            result = {'user': None, 'revoked': True}
        else:
            assert body.get('accessToken') == TOKEN
            if operation == 'history':
                result = {'ownerId': OWNER, 'revision': 1, 'snapshot': {'format': 'dua-cloud-snapshot', 'version': 1, 'memory': [],
                    'chats': [{'id': 'cloud-chat', 'model': 'Dua', 'title': 'Cloud note', 'pinned': True, 'updatedAt': 1,
                               'messages': [{'role': 'user', 'text': 'A saved question'}, {'role': 'model', 'text': 'A saved Android answer.'}]}]}}
            elif operation == 'chat':
                text = body['messages'][-1]['text']
                if text == 'fail this request':
                    status = 503
                    result = {'error': 'The server is temporarily unavailable. Retry.'}
                else:
                    result = {'text': 'Connection test answer: ' + text}
            else:
                raise AssertionError(operation)
        route.fulfill(status=status, content_type='application/json', body=json.dumps(result))

    page.route('**/src/main.tsx*', mock_sdk_entry)
    page.route('**/api/dua/**', backend)
    page.goto('http://127.0.0.1:4175/', wait_until='networkidle')
    expect(page.get_by_role('heading', name='Welcome to Dua AI')).to_be_visible()
    page.get_by_label('Email', exact=True).fill('other@example.invalid')
    page.get_by_label('Password', exact=True).fill('test-only-password')
    page.get_by_role('button', name='Sign in', exact=True).click()
    expect(page.get_by_role('alert')).to_contain_text('private assistant')
    page.get_by_label('Email', exact=True).fill('reader@example.invalid')
    page.get_by_role('button', name='Sign in', exact=True).click()
    expect(page.get_by_role('heading', name='Hi Reader, what’s on your mind?')).to_be_visible()
    expect(page.get_by_role('button', name='Task', exact=True)).to_be_disabled()

    for width in [320, 375, 768, 1440]:
        page.set_viewport_size({'width': width, 'height': 900})
        for theme in ['light', 'dark']:
            page.get_by_role('button', name='Profile and settings').click()
            page.get_by_label('Appearance', exact=True).select_option(theme)
            page.get_by_role('button', name='Close settings').click()
            assert page.locator('html').get_attribute('data-theme') == theme
            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
            assert page.get_by_role('heading', name='Hi Reader, what’s on your mind?').count() == 1
            if width in [375, 1440]:
                page.screenshot(path=str(OUTPUT / f'home-{width}-{theme}.png'))

    page.set_viewport_size({'width': 390, 'height': 844})
    field = page.get_by_role('textbox', name='Message Dua AI')
    field.fill('   ')
    field.press('Enter')
    assert page.locator('.msg-user').count() == 0
    field.fill('<img src=x onerror=alert(1)>')
    field.press('Enter')
    expect(page.locator('.msg-ai').last).to_contain_text('Connection test answer:')
    assert page.locator('.msg-user img').count() == 0
    assert page.get_by_text('Preview response', exact=True).count() == 0
    field.fill('fail this request')
    field.press('Enter')
    expect(page.get_by_role('alert')).to_contain_text('temporarily unavailable')
    expect(page.get_by_role('button', name='Retry request')).to_be_visible()
    page.get_by_role('button', name='New chat', exact=True).last.click()
    page.get_by_role('button', name='Open history').click()
    expect(page.get_by_role('button', name='Cloud note', exact=False)).to_be_visible()
    page.get_by_role('button', name='Cloud note', exact=False).click()
    expect(page.get_by_text('A saved Android answer.', exact=True)).to_be_visible()
    expect(field).to_be_disabled()
    page.get_by_role('button', name='Start a new chat').click()
    expect(field).to_be_enabled()
    page.get_by_role('button', name='Profile and settings').click()
    page.get_by_role('button', name='Sign out', exact=True).click()
    expect(page.get_by_role('heading', name='Welcome to Dua AI')).to_be_visible()
    assert page.locator('.msg-ai').count() == 0
    assert not errors, errors
    browser.close()
    report = {'result': 'passed', 'backend': 'controlled fixtures', 'responsiveCases': 8,
              'checks': ['owner denial', 'sign-in', 'one greeting', 'real-response rendering', 'no HTML injection',
                         'provider failure', 'read-only cloud history', 'new chat', 'sign-out'], 'pageErrors': errors}
    (OUTPUT / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
