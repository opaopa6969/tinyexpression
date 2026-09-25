// "Create PR" helper of the Catalog panel (issue #201, stage 4). With a token (kept only in
// memory by the caller, never stored) it commits the edited catalog and its derived files to a
// new branch through the GitHub REST API and opens a pull request; without one it builds the
// URL of GitHub's web editor for the catalog file.

const API = 'https://api.github.com';

async function gh(token, method, path, body) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'X-GitHub-Api-Version': '2022-11-28',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const json = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(`${method} ${path}: HTTP ${response.status} ${json?.message ?? ''}`.trim());
    error.status = response.status;
    throw error;
  }
  return json;
}

/**
 * Commits `files` (Map of repository path → text) on a new branch off `base` and opens a PR.
 * @returns {branch, commit, pullRequest: {number, html_url} | null, compareUrl}
 */
export async function createCatalogPullRequest({ token, owner, repo, base = 'master', branch, title, body, files, onProgress = () => {} }) {
  const r = `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`;
  onProgress(`${base} の先頭を取得`);
  const head = await gh(token, 'GET', `${r}/git/ref/heads/${encodeURIComponent(base)}`);
  const parent = head.object.sha;
  const parentCommit = await gh(token, 'GET', `${r}/git/commits/${parent}`);
  onProgress(`${files.size} ファイルの tree を作成`);
  const tree = await gh(token, 'POST', `${r}/git/trees`, {
    base_tree: parentCommit.tree.sha,
    tree: [...files].map(([path, content]) => ({ path, mode: '100644', type: 'blob', content })),
  });
  const commit = await gh(token, 'POST', `${r}/git/commits`, { message: `${title}\n\n${body}`, tree: tree.sha, parents: [parent] });
  onProgress(`ブランチ ${branch} を作成`);
  await gh(token, 'POST', `${r}/git/refs`, { ref: `refs/heads/${branch}`, sha: commit.sha });
  const compareUrl = compareUrlOf({ owner, repo, base, branch, title, body });
  onProgress('pull request を作成');
  try {
    const pullRequest = await gh(token, 'POST', `${r}/pulls`, { title, head: branch, base, body });
    return { branch, commit: commit.sha, pullRequest, compareUrl };
  } catch (error) {
    // The branch exists; the compare page lets the user open the PR by hand.
    return { branch, commit: commit.sha, pullRequest: null, compareUrl, error: error.message };
  }
}

/** GitHub's compare page with the PR form prefilled. */
export function compareUrlOf({ owner, repo, base = 'master', branch, title, body }) {
  const query = new URLSearchParams({ expand: '1', title, body });
  return `https://github.com/${owner}/${repo}/compare/${encodeURIComponent(base)}...${encodeURIComponent(branch)}?${query}`;
}

/** GitHub's web editor for a file (propose changes → PR, with a fork when needed). */
export function editUrlOf({ owner, repo, base = 'master', path }) {
  return `https://github.com/${owner}/${repo}/edit/${encodeURIComponent(base)}/${path}`;
}
