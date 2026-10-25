import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccount } from '../src/core/account.mjs';
import { mockFetch } from './helpers.mjs';
const id = '11111111-1111-4111-8111-111111111111';
const email = 'fareed@example.com';
const user = (phone = '', overrides = {}) => Response.json({ id, email, user_metadata: { full_name: 'Fareed', avatar_url: 'https://example.com/photo', dua_phone_number: phone }, ...overrides });
const env = fetcher => ({ url: 'https://project.supabase.co', key: 'public-anon-key', allowedEmail: email, fetcher });
const request = (phoneNumber, extras = {}, headers = {}) => new Request('https://dua.example/api/account/phone', {
    method: 'PUT', headers: { authorization: 'Bearer access', 'x-dua-native': '1', 'content-type': 'application/json', ...headers },
    body: JSON.stringify({ phoneNumber, expectedAccountId: id, ...extras })
});
test('phone updates use only the verified owner token and a single contact metadata key', async () => {
    for (const [input, expected] of [['+91 (98765) 43210', '+919876543210'], ['', '']]) {
        const fetcher = mockFetch(user(), user(expected));
        const response = await handleAccount(request(input), env(fetcher));
        assert.equal(response.status, 200);
        const result = await response.json();
        assert.equal(result.phoneUpdated, true); assert.equal(result.ownerId, id);
        assert.equal(result.user.phoneNumber, expected);
        assert.equal(result.user.displayName, 'Fareed'); assert.equal(result.user.avatarUrl, 'https://example.com/photo');
        assert.equal(fetcher.calls[1].init.method, 'PUT');
        assert.equal(fetcher.calls[1].init.headers.Authorization, 'Bearer access');
        assert.deepEqual(fetcher.calls[1].body, { data: { dua_phone_number: expected } });
        assert.equal(response.headers.get('Cache-Control'), 'no-store');
    }
});
test('phone updates reject malformed input, extra fields and account changes before writing', async () => {
    for (const [value, extras, status] of [[123, {}, 400], ['9876543210', {}, 400], ['+01', {}, 400], ['+1234567890123456', {}, 400], ['+91\n9876543210', {}, 400], ['+919876543210', { password: 'bad' }, 400], ['+919876543210', { expectedAccountId: 'other' }, 409]]) {
        const fetcher = mockFetch(user());
        assert.equal((await handleAccount(request(value, extras), env(fetcher))).status, status);
        assert.equal(fetcher.calls.length, 1);
    }
});
test('phone updates require authentication and configured owner lock', async () => {
    const stranger = mockFetch(user('', { email: 'stranger@example.com' }));
    assert.equal((await handleAccount(request('+919876543210'), env(stranger))).status, 403);
    assert.equal(stranger.calls.length, 1);
    const missing = mockFetch(new Response('{}', { status: 401 }));
    assert.equal((await handleAccount(request('+919876543210'), env(missing))).status, 401);
    const noOwner = mockFetch(user());
    assert.equal((await handleAccount(request('+919876543210'), { ...env(noOwner), allowedEmail: '' })).status, 503);
    assert.equal(noOwner.calls.length, 1);
    const origin = mockFetch();
    assert.equal((await handleAccount(request('+919876543210', {}, { origin: 'https://evil.example' }), env(origin))).status, 403);
    assert.equal(origin.calls.length, 0);
});
test('phone updates reject unconfirmed results and preserve useful retry failures', async () => {
    for (const [reply, status] of [[user('+917777777777'), 502], [user('+919876543210', { id: '22222222-2222-4222-8222-222222222222' }), 502], [new Response('{}', { status: 429 }), 429], [new Response('{}', { status: 500 }), 502]]) {
        const response = await handleAccount(request('+919876543210'), env(mockFetch(user(), reply)));
        assert.equal(response.status, status);
        assert.equal((await response.json()).phoneUpdated, undefined);
    }
});
test('saved contact is returned on authenticated session reads and legacy metadata remains compatible', async () => {
    for (const [saved, expected] of [['+919876543210', '+919876543210'], ['', ''], ['garbage', '']]) {
        const response = await handleAccount(new Request('https://dua.example/api/account/session', { headers: { authorization: 'Bearer access', 'x-dua-native': '1' } }), env(mockFetch(user(saved))));
        assert.equal((await response.json()).user.phoneNumber, expected);
    }
});
