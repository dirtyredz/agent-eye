import * as fs from 'node:fs/promises';
import { join, resolve, relative } from 'node:path';
import { tmpdir } from 'node:os';
import { requireThat } from '../releases/contract.mjs';
import { onTermination } from '../tooling/process.mjs';

export async function protectedDirectory({ run, root, io = fs, tempRoot = tmpdir(), platform = process.platform }) {
  requireThat(platform === 'win32', 'Owner key operations require Windows ACL protection');
  const dir = await io.mkdtemp(join(tempRoot, 'agent-eye-key-'));
  try {
    const rel = relative(resolve(root), resolve(dir));
    requireThat(rel.startsWith('..') || /^[A-Za-z]:/.test(rel), 'Key directory must be outside checkout');
    // Set a protected DACL with exactly one full-control ACE; verify owner, inheritance,
    // SID and rights before returning permission to write any key bytes.
    const script = `$ErrorActionPreference='Stop'; $p=$env:AGENT_EYE_KEY_DIR; $sid=[System.Security.Principal.WindowsIdentity]::GetCurrent().User; $acl=New-Object System.Security.AccessControl.DirectorySecurity; $acl.SetOwner($sid); $acl.SetAccessRuleProtection($true,$false); $rule=New-Object System.Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'); $acl.AddAccessRule($rule); Set-Acl -LiteralPath $p -AclObject $acl; $check=Get-Acl -LiteralPath $p; $rules=@($check.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier])); if (!$check.AreAccessRulesProtected -or $check.GetOwner([System.Security.Principal.SecurityIdentifier]).Value -ne $sid.Value -or $rules.Count -ne 1 -or $rules[0].IdentityReference.Value -ne $sid.Value -or $rules[0].IsInherited -or $rules[0].AccessControlType -ne 'Allow' -or $rules[0].FileSystemRights -ne 'FullControl') { throw 'Key directory ACL verification failed' }`;
    await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], { env: { AGENT_EYE_KEY_DIR: dir } });
    return { dir, path: join(dir, 'agent-eye.p12'), cleanup: () => io.rm(dir, { recursive: true, force: true }) };
  } catch (error) { await io.rm(dir, { recursive: true, force: true }); throw error; }
}
export async function withKeyDirectory(deps, action) {
  const key = await protectedDirectory(deps);
  const removeCleanup = onTermination(key.cleanup);
  try { return await action(key); }
  finally { try { await key.cleanup(); } finally { removeCleanup(); } }
}
