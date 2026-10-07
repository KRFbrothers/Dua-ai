# Web architecture

The web project keeps its source modules, Vercel route adapters, static client, tests and build verification separate:

| Path | Responsibility |
| --- | --- |
| `src/core/` | Provider integrations, account/cloud logic, attachments, search, page reading and shared domain utilities |
| `src/server/` | Local development HTTP server, request dispatch, Vercel handler and request guard |
| `api/` | Vercel serverless function entry points; Vercel maps these files to `/api/*` routes |
| `dist/` | Static client files served by Vercel and the local server |
| `test/` | Node regression tests for the static client and server-side modules |
| `scripts/` | Static-site build verification |

`npm start`, `npm test` and `npm run build` are run from this directory. Keep the Vercel `api/` and `dist/` paths stable: `web/vercel.json` routes functions from `api/` and deploys `dist/`, while the local server serves the same static directory.

API credentials belong in server environment variables, never in `dist/` or client-side configuration.
