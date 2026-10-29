"""Browser checks for the static preview. Requires Python Playwright and Chromium."""
import argparse
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

ROUTES = ['home', 'task', 'chat', 'history', 'models', 'projects', 'library',
          'canvas', 'voice', 'profile', 'settings', 'appearance', 'notifications', 'storage']


def verify(url: str, output: Path, offline_html: Path | None = None) -> None:
    output.mkdir(parents=True, exist_ok=True)
    errors, failures, cases = [], [], []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(executable_path='/usr/bin/chromium',
            args=['--no-sandbox', '--disable-dev-shm-usage'])
        context = browser.new_context(viewport={'width': 1440, 'height': 1080},
                                      permissions=['clipboard-read', 'clipboard-write'])
        if offline_html:
            html = offline_html.read_text()

            def offline_response(request_route):
                if request_route.request.url == url.split('#')[0]:
                    request_route.fulfill(body=html, content_type='text/html')
                else:
                    failures.append(request_route.request.url)
                    request_route.abort()

            # Serve the exported document from memory and block all asset/network requests.
            context.route('**/*', offline_response)
        page = context.new_page()
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.on('console', lambda message: errors.append(message.text) if message.type == 'error' else None)
        page.on('requestfailed', lambda request: failures.append(request.url))
        page.goto(url, wait_until='networkidle')

        def route(name):
            if page.viewport_size['width'] > 760:
                page.locator(f'.rail [data-route="{name}"]').click()
            else:
                page.locator('#screen-select').select_option(name)
            assert page.locator('body').get_attribute('data-screen') == name

        for width in [320, 768, 1024, 1440]:
            page.set_viewport_size({'width': width, 'height': 900})
            for theme in ['light', 'dark']:
                page.locator(f'.toolbar [data-theme-mode="{theme}"]').click()
                for name in ROUTES:
                    route(name)
                    issues = page.evaluate('''() => {
                      const phone = document.getElementById('phone-content');
                      const targets = [...document.querySelectorAll('button:not(:disabled),a[href],select,input:not([type=file]),textarea')];
                      return {
                        pageOverflow: document.documentElement.scrollWidth > innerWidth + 1,
                        phoneOverflow: phone.scrollWidth > phone.clientWidth + 1,
                        smallTargets: targets.filter(element => {
                          const r = element.getBoundingClientRect();
                          return r.width > 0 && r.height > 0 && (r.width < 47.9 || r.height < 47.9);
                        }).map(element => element.getAttribute('aria-label') || element.textContent.trim()),
                        missingIcons: [...phone.querySelectorAll('use')].filter(element => !document.getElementById(element.getAttribute('href').slice(1))).length,
                        brokenImages: [...document.images].filter(image => !image.complete || !image.naturalWidth).length,
                        headings: phone.querySelectorAll('#screen-title').length
                      };
                    }''')
                    assert not issues['pageOverflow'], (width, theme, name, issues)
                    assert not issues['phoneOverflow'], (width, theme, name, issues)
                    assert not issues['smallTargets'], (width, theme, name, issues)
                    assert not issues['missingIcons'] and not issues['brokenImages'], (name, issues)
                    assert issues['headings'] == 1, (name, issues)
                    cases.append({'width': width, 'theme': theme, 'screen': name})
                    if width == 1440 and name in ['home', 'task', 'chat', 'appearance']:
                        page.screenshot(path=str(output / f'desktop-{name}-{theme}.png'), full_page=True)

        page.set_viewport_size({'width': 390, 'height': 844})
        for theme in ['light', 'dark']:
            page.locator(f'.toolbar [data-theme-mode="{theme}"]').click()
            for name in ROUTES:
                route(name)
                page.locator('.phone').screenshot(path=str(output / f'mobile-{name}-{theme}.png'))

        route('home')
        page.locator('#message-input').fill('Keep this draft')
        page.locator('[data-mode="task"]').click()
        assert page.locator('#message-input').input_value() == 'Keep this draft'
        assert page.locator('#phone-content #screen-title').inner_text() == 'What can I tackle for you?'
        page.locator('[data-starter]').first.click()
        assert page.locator('#message-input').input_value() == 'Nudge me to back up my photos'
        page.locator('[data-mode="home"]').click()
        page.locator('[data-go="models"]').click()
        page.locator('[data-model="Dua"]').click()
        page.locator('[data-model="Dua"]').press('ArrowDown')
        assert page.locator('[data-model="Gemini"]').get_attribute('aria-checked') == 'true'
        page.locator('[data-model="Gemini"]').press('ArrowUp')
        page.locator('[data-action="back"]').last.click()
        assert page.locator('#message-input').input_value() == 'Nudge me to back up my photos'
        assert page.locator('.appbar .model').inner_text() == 'Dua'
        page.locator('#message-input').fill('  ')
        assert page.locator('[aria-label="Send message"]').count() == 0
        assert page.locator('[aria-label="Voice mode"]').is_visible()
        page.locator('#message-input').press('Enter')
        assert page.locator('body').get_attribute('data-screen') == 'home'
        hostile = '<img src=x onerror=alert(1)> & "hello"'
        page.locator('#message-input').fill(hostile)
        page.locator('#message-input').press('Enter')
        assert page.locator('.message.user').last.inner_text() == hostile
        assert page.locator('.message.user img').count() == 0
        assert page.locator('.sample-label').last.text_content() == 'Preview response'
        page.locator('[data-copy]').last.click()
        assert page.evaluate('navigator.clipboard.readText()').startswith('This is a preview reply')

        route('history')
        page.locator('#history-search').fill('no matching title 123')
        assert page.locator('#history-list').inner_text().startswith('No matching chats')
        page.locator('[data-action="clear-search"]').click()
        assert page.locator('[data-chat]').count() == 4
        page.locator('[data-filter="pinned"]').click()
        assert page.locator('[data-chat]').count() == 1
        page.locator('[data-chat]').click()
        assert 'focused morning' in page.locator('.message.user').inner_text()

        route('projects')
        page.locator('[data-action="new-project"]').click()
        assert page.locator('#modal').evaluate('(dialog)=>dialog.open')
        assert page.locator('#project-name').evaluate('(input)=>input === document.activeElement')
        page.locator('#project-name').fill('My next idea')
        page.locator('#project-name').press('Enter')
        assert page.locator('.project-card').count() == 3
        page.locator('.project-card').last.click()
        assert page.locator('.document h3').inner_text() == 'My next idea'
        page.locator('#canvas-editor').fill('A note worth keeping.')
        route('home')
        route('canvas')
        assert page.locator('#canvas-editor').input_value() == 'A note worth keeping.'

        route('profile')
        page.locator('#display-name').fill('Reader Example')
        page.locator('#profile-form button').click()
        route('home')
        assert page.locator('#screen-title').inner_text().startswith('Hi Reader,')
        page.reload(wait_until='networkidle')
        assert page.locator('#screen-title').inner_text().startswith('Hi Reader,')

        route('appearance')
        page.locator('.theme-option[data-theme-mode="light"]').click()
        page.locator('.theme-option[data-theme-mode="light"]').press('ArrowRight')
        assert page.locator('html').get_attribute('data-theme') == 'dark'
        page.reload(wait_until='networkidle')
        assert page.locator('html').get_attribute('data-theme') == 'dark'
        page.locator('.theme-option[data-theme-mode="system"]').click()
        page.emulate_media(color_scheme='light')
        page.wait_for_function("document.documentElement.dataset.theme === 'light'")
        assert page.locator('html').get_attribute('data-theme') == 'light'
        page.emulate_media(color_scheme='dark')
        page.wait_for_function("document.documentElement.dataset.theme === 'dark'")
        assert page.locator('html').get_attribute('data-theme') == 'dark'

        route('notifications')
        page.locator('[data-switch="all"]').click()
        assert page.locator('[data-switch="all"]').get_attribute('aria-checked') == 'false'
        assert page.locator('[data-switch="replies"]').is_disabled()
        route('voice')
        page.locator('[data-action="voice-play"]').click()
        assert page.locator('.voice-orb').evaluate('(orb)=>orb.classList.contains("playing")')
        page.emulate_media(reduced_motion='reduce')
        assert page.locator('.voice-orb').evaluate('(orb)=>getComputedStyle(orb).animationName') == 'none'
        route('library')
        page.locator('#file-picker').set_input_files({'name': 'notes <sample>.txt', 'mimeType': 'text/plain', 'buffer': b'Local example'})
        assert 'notes <sample>.txt' in page.locator('#phone-content').inner_text()
        route('storage')
        assert '2 documents in this preview' in page.locator('.storage-summary').inner_text()
        page.locator('[data-action="clear-history"]').click()
        page.keyboard.press('Escape')
        assert not page.locator('#modal').evaluate('(dialog)=>dialog.open')
        page.locator('[data-action="clear-history"]').click()
        page.locator('[data-action="confirm-clear"]').click()
        assert page.locator('.storage-summary h3').inner_text() == '0 chats'
        route('profile')
        page.locator('#display-name').fill('A' * 48)
        page.locator('#profile-form button').click()
        for width in [320, 390]:
            page.set_viewport_size({'width': width, 'height': 844})
            for name in ['profile', 'home', 'task', 'history']:
                route(name)
                overflow = page.locator('#phone-content').evaluate('''element =>
                    [element, ...element.querySelectorAll('.scroll,.home-main,.history-footer')]
                    .filter(item => item.scrollWidth > item.clientWidth + 1)
                    .map(item => item.className)''')
                assert not overflow, (width, name, overflow)
        assert not errors and not failures, {'errors': errors, 'failedRequests': failures}
        browser.close()
    report = {'result': 'passed', 'layoutCases': len(cases), 'widths': [320, 768, 1024, 1440],
              'themes': ['light', 'dark'], 'screens': len(ROUTES), 'consoleErrors': errors,
              'failedRequests': failures, 'interactionChecks': 'passed', 'offlineExport': bool(offline_html)}
    (output / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:4173/')
    parser.add_argument('--output', type=Path, default=Path('/tmp/dua-preview-checks'))
    parser.add_argument('--offline-html', type=Path)
    arguments = parser.parse_args()
    verify(arguments.url, arguments.output, arguments.offline_html)
