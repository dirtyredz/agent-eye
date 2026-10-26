// Single source for which commands need the owner (Windows Hello, 1Password) vs CI. Light: safe to import on dry-run.
export const OWNER_COMMANDS = new Set(['setup', 'setup-vault', 'provision', 'generate-key', 'verify-key', 'inspect-vault', 'cleanup-orphans', 'release-sign']);
export const CI_COMMANDS = new Set(['validate-tag', 'stage', 'draft']);
