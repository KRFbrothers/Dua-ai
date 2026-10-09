import test from 'node:test';
import assert from 'node:assert/strict';
import { handleAccount } from '../src/core/account.mjs';
import { mockFetch } from './helpers.mjs';

const env = fetcher => ({ url: 'https://project.supabase.co', key: 'public-anon-key', fetcher });
const id = '11111111-1111-4111-8111-111111111111';
const session = metadata => Response.json({ access_token: 'access', refresh_token: 'refresh', expires_in: 3600, user: { id, email: 'fareed@example.com', user_metadata: metadata } });
const googleSignIn = fetcher => handleAccount(new Request('https://dua.example/api/account/google-token', {
  method: 'POST', headers: { 'content-type': 'application/json', 'x-dua-native': '1' },
  body: JSON.stringify({ idToken: 'header.payload.signature', nonce: 'raw-nonce-0123456789abcdef' })
}), env(fetcher));

test('Google sign-in names the account from Google metadata, falling back to a saved display name', async () => {
  const cases = [
    [{ full_name: 'Fareed Ahmad', name: 'Other', display_name: 'Old' }, 'Fareed Ahmad'],
    [{ name: ' Fareed ', display_name: 'Old' }, 'Fareed'],
    [{ full_name: '  ', display_name: 'Saved Name' }, 'Saved Name'],
    [{}, ''],
    [undefined, ''],
  ];
  for (const [metadata, expected] of cases) {
    const fetcher = mockFetch(session(metadata));
    const response = await googleSignIn(fetcher);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.user.displayName, expected);
    assert.equal(body.user.email, 'fareed@example.com');
    assert.match(fetcher.calls[0].url, /\/auth\/v1\/token\?grant_type=id_token$/);
    assert.equal(fetcher.calls[0].body.nonce, 'raw-nonce-0123456789abcdef');
  }
});

test('the removed profile-name route is no longer served', async () => {
  const response = await handleAccount(new Request('https://dua.example/api/account/profile', {
    method: 'PATCH', headers: { 'content-type': 'application/json', 'x-dua-native': '1' }, body: JSON.stringify({ displayName: 'New' })
  }), env(mockFetch()));
  assert.equal(response.status, 405);
});
