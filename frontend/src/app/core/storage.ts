/** Acesso ao localStorage tolerante a navegação privada e bloqueios do navegador. */
export const storage = {
  read<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  },
  write(key: string, value: unknown): void {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // armazenamento indisponível: o estado continua apenas em memória
    }
  },
  remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      // idem
    }
  },
};
