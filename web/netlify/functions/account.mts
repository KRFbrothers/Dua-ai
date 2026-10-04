import {handleAccount,handleCloudRead} from '../../account.mjs';
declare const Netlify: {env:{get(name:string):string|undefined}};
export default async function handler(req:Request){const env={url:Netlify.env.get('SUPABASE_URL'),key:Netlify.env.get('SUPABASE_ANON_KEY')};return new URL(req.url).pathname==='/api/cloud'&&req.method==='GET'?handleCloudRead(req,env):handleAccount(req,env);}
export const config={path:['/api/account/:action','/api/cloud'],rateLimit:{windowLimit:20,windowSize:60,aggregateBy:['ip','domain'],action:'rewrite',to:'/api/account-rate-limit'}};
