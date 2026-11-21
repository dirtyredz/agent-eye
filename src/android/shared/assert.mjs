// Leaf: imports nothing. Shared by releases/ and signing/.
export const hex = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
export function requireThat(condition, message) {
  if (!condition) throw new Error(message);
}
