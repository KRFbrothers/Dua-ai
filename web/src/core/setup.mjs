import { providerConfiguration } from './providers.mjs';
import { accountCapabilities } from './account.mjs';
import { searchConfiguration } from './search.mjs';
import { advancedConfiguration } from './advanced-integrations.mjs';

export function setupConfiguration(env = {},requestUrl = env.SUPABASE_AUTH_REDIRECT_URL) {
 const providers = providerConfiguration(env);
 const account = {...accountCapabilities({url:env.SUPABASE_URL,key:env.SUPABASE_ANON_KEY,oauthRedirectUrl:env.SUPABASE_AUTH_REDIRECT_URL,serviceRoleKey:env.SUPABASE_SERVICE_ROLE_KEY},requestUrl),requireLogin:['1','true'].includes(String(env.DUA_REQUIRE_LOGIN).toLowerCase()) || Boolean(env.DUA_ALLOWED_EMAIL?.trim()) || Number(env.DUA_DAILY_REQUEST_LIMIT)>0,ownerRestricted:Boolean(env.DUA_ALLOWED_EMAIL?.trim()),dailyRequestLimit:Number(env.DUA_DAILY_REQUEST_LIMIT) || 0};
 const search = searchConfiguration(env);
 const advanced = advancedConfiguration(env);
 const group=(id,title,state,requires,limitations)=>({id,title,state,requires,limitations});
 return {providers,account,search,advanced,groups:[
  group('providers','AI providers',providers.slice(2).every(p=>p.configured)?'configured':providers.some(p=>p.configured)?'partial':'needs-configuration',['API key and model for each provider'],['All providers accept TXT. Image/PDF support depends on configured model capabilities; Grok/Kimi do not accept PDF.']),
  group('search','Web search and page reading',search.configured?'configured':'partial',['Brave Search API key for Search','Public HTTPS URL for Read link'],['Chat Web mode starts Off. Search uses snippets; Read link reads one public text page. Login pages, JavaScript rendering and crawling are unsupported.']),
  group('sync','Automatic cloud sync',account.configured?'available':'needs-configuration',['Supabase cloud migration','Sign in and enable on this device'],['Defaults off. Conflicts and deletions pause for review. No file or audio bytes.']),
  group('memory','Memory suggestions','available',['Enable suggestions and approve each note'],['Explicit remember statements become proposals; saved notes are used only when memory is enabled.']),
  group('connectors','Connectors and plugins',advanced.connectors.github.configured&&env.DUA_ALLOWED_EMAIL?'configured':'needs-configuration',['GitHub read-only token','Allowed personal account email','Enable connector and sign in'],['Only repository listing is supported. Other tools and OAuth require separate implementations.']),
  group('computer','Screen and video','partial',['Device camera app for photo attachments'],['Android can attach a captured photo to a chat. No screen sharing, video upload, recording or remote computer control. Native screen capture and remote sessions remain pending.']),
  group('voice','Advanced voice','partial',['Device speech service','Optional generated voice key, voice ID and sign in'],['Foreground device speech works. Generated audio is explicit. Full duplex and background sessions remain pending.']),
  group('account','Account and access protection',account.configured?'partial':'needs-configuration',['Supabase email configuration','Optional Google provider and deletion service key','Optional owner and daily request policy'],['Email recovery is available after SMTP setup. Google sign-in uses native Android Google Sign-In verified by the backend. Request limits are not currency spending limits.'])
 ]};
}
export function handleSetup(req,{env={}}={}) {
 if(req.method!=='GET')return Response.json({error:'Method not allowed.'},{status:405,headers:{'Cache-Control':'no-store'}});
 return Response.json(setupConfiguration(env,req.url),{headers:{'Cache-Control':'no-store'}});
}
