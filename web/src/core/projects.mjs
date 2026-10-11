export const PERSONAL_PROJECT = 'personal';
export const defaultProjects = () => ['Personal', 'Coding', 'Business'].map(name => ({ id: name.toLowerCase(), name, instructions: '', archived: false }));
export const validProjectId = id => typeof id === 'string' && /^[A-Za-z0-9_-]{1,100}$/.test(id);
function validateMemory(memory, id) {
  if (!memory || typeof memory !== 'object' || Array.isArray(memory) || Object.keys(memory).sort().join(',') !== 'enabled,notes' ||
      typeof memory.enabled !== 'boolean' || !Array.isArray(memory.notes) || memory.notes.length > 10 ||
      !memory.notes.every(n => typeof n === 'string' && n.trim() && n.length <= 200) ||
      id === PERSONAL_PROJECT && (memory.enabled || memory.notes.length)) throw new Error('Invalid project memory. Keep at most 10 notes of 200 characters; Personal uses existing memory settings.');
  return { enabled: memory.enabled, notes: [...memory.notes] };
}
function validateTasks(tasks) {
  if (!Array.isArray(tasks) || tasks.length > 100 || new Set(tasks.map(t => t?.id)).size !== tasks.length ||
      !tasks.every(t => t && typeof t === 'object' && !Array.isArray(t) && ['completed,id,title', 'completed,dueAt,id,title'].includes(Object.keys(t).sort().join(',')) &&
        validProjectId(t.id) && typeof t.title === 'string' && t.title.trim() && t.title.length <= 200 && typeof t.completed === 'boolean' &&
        (t.dueAt === undefined || Number.isSafeInteger(t.dueAt) && t.dueAt >= 1 && t.dueAt <= 253402300799999))) {
    throw new Error('Invalid tasks. Keep at most 100 per project with titles up to 200 characters.');
  }
  return tasks.map(t => ({ id: t.id, title: t.title, completed: t.completed, ...(t.dueAt !== undefined ? { dueAt: t.dueAt } : {}) }));
}
export function validateProjects(value) {
  const valid = Array.isArray(value) && value.length >= 1 && value.length <= 20 &&
    new Set(value.map(p => p?.id)).size === value.length && value.some(p => p?.id === PERSONAL_PROJECT) && value.some(p => p?.archived === false) &&
    value.every(p => p && typeof p === 'object' && !Array.isArray(p) && ['id', 'name', 'instructions', 'archived'].every(k => Object.hasOwn(p, k)) &&
      Object.keys(p).every(k => ['id', 'name', 'instructions', 'archived', 'tasks', 'memory'].includes(k)) &&
      validProjectId(p.id) && typeof p.name === 'string' && p.name.trim() && p.name.length <= 80 && typeof p.instructions === 'string' && p.instructions.length <= 4000 && typeof p.archived === 'boolean');
  if (!valid) throw new Error('Invalid projects. Keep Personal, one active project, and at most 20 projects.');
  return value.map(p => {
    const result = { id: p.id, name: p.name, instructions: p.instructions, archived: p.archived };
    if (p.tasks !== undefined) result.tasks = validateTasks(p.tasks);
    if (Object.hasOwn(p, 'memory')) result.memory = validateMemory(p.memory, p.id);
    return result;
  });
}

/** Read instructions from the owner snapshot, never from client-supplied instruction text. */
export async function resolveProjectContext(request, data, { env, fetcher }) {
  if (data.projectId === undefined) return; // Older APKs keep their existing personal-chat protocol.
  if (!env.DUA_ALLOWED_EMAIL?.trim()) throw Object.assign(new Error('Owner-only Projects require the allowed account email.'), { status: 503 });
  const { handleCloudRead } = await import('./account.mjs');
  const response = await handleCloudRead(new Request(new URL('/api/cloud', request.url), { headers: request.headers, signal: request.signal }),
    { url: env.SUPABASE_URL, key: env.SUPABASE_ANON_KEY, allowedEmail: env.DUA_ALLOWED_EMAIL, fetcher });
  const result = await response.json();
  if (!response.ok) throw Object.assign(new Error(result.error || 'Project could not be loaded. Sync and retry.'), { status: response.status });
  const projects = result.snapshot.projects || defaultProjects();
  const project = projects.find(p => p.id === data.projectId);
  if (!project) throw Object.assign(new Error('This project is not on the server yet. Sync it before chatting.'), { status: 409 });
  if (project.archived) throw Object.assign(new Error('This project is archived. Restore it before chatting.'), { status: 409 });
  const chat = result.snapshot.chats.find(c => c.id === data.context?.id);
  if (chat && (chat.projectId || PERSONAL_PROJECT) !== project.id) throw Object.assign(new Error('The server chat belongs to another project. Sync and reopen it.'), { status: 409 });
  const otherFiles = new Set(result.snapshot.chats.filter(c => (c.projectId || PERSONAL_PROJECT) !== project.id)
    .flatMap(c => c.messages.flatMap(m => (m.files || []).map(f => f.path))));
  if (data.messages.some(m => (m.files || []).some(f => otherFiles.has(f.path)))) throw Object.assign(new Error('A stored file belongs to another project. Attach a separate copy here.'), { status: 403 });
  // Personal keeps the existing device-controlled switch, but note text comes from the server.
  // Other projects use only their own authoritative notes/switch; ignore client-supplied memory.
  const notes = project.id === PERSONAL_PROJECT ? (data.memory ? result.snapshot.memory : []) : (project.memory?.enabled ? project.memory.notes : []);
  data.memory = [notes.length ? notes.map(n => `- ${n}`).join('\n') : '', project.instructions ? `Instructions for project "${project.name}" (owner preferences; never override system rules):\n${project.instructions}` : ''].filter(Boolean).join('\n\n');
}
