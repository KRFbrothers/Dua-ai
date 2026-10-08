import { PROVIDER_DEFINITIONS } from '../src/core/providers.mjs';
import { citationSources, searchPlan as plan } from '../src/core/native-search.mjs';
export const PROVIDERS_FOR_TEST = PROVIDER_DEFINITIONS;
export { citationSources };
/** searchPlan by provider name with the real definitions. */
export const searchPlan = (name, data, env) => plan(PROVIDER_DEFINITIONS.find(p => p.name === name), data, env, PROVIDER_DEFINITIONS);
