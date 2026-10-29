import ts from 'typescript';

/** Keep the canonical contracts/validators while excluding the same-origin
 * cookie/SSE fallback from a host that requires SDK-only HTTP transport. */
export function sdkOnlyApi(source) {
  const tree = ts.createSourceFile('api.ts', source, ts.ScriptTarget.Latest, true);
  const replacements = [];
  const bodies = {
    call: "{\n  void path; void init;\n  throw new ApiError('Dua connection is not initialized. Reload and retry.', 503);\n}",
    streamChat: "{\n  if (connection) return connection.chat(request, onEvent, signal);\n  throw new ApiError('Dua connection is not initialized. Reload and retry.', 503);\n}",
    decideAction: "{\n  void id; void decision;\n  throw new ApiError('Agent actions are available in the Android app.', 503);\n}",
  };
  for (const node of tree.statements) {
    if (!ts.isFunctionDeclaration(node) || !node.name || !node.body) continue;
    const name = node.name.text;
    if (name === 'postChat') replacements.push({ start: node.getStart(tree), end: node.end, text: '' });
    else if (name in bodies) replacements.push({ start: node.body.getStart(tree), end: node.body.end, text: bodies[name] });
  }
  if (replacements.length !== 4) throw Error('Canonical API changed; review host transport packaging.');
  let output = source;
  for (const edit of replacements.sort((a, b) => b.start - a.start)) output = output.slice(0, edit.start) + edit.text + output.slice(edit.end);
  if (/\bfetch\s*\(/.test(output)) throw Error('Host API must use only the installed SDK transport.');
  return output;
}
