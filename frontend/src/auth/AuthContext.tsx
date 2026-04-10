import { createContext, useContext, useEffect, useState } from 'react';
import { message } from 'antd';
import type { LoginPayload, UserProfile } from '../lib/api';
import { api } from '../lib/api';
import { authStorage } from './authStorage';

type AuthContextValue = {
  user?: UserProfile;
  loading: boolean;
  isAdmin: boolean;
  login: (payload: LoginPayload) => Promise<UserProfile>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfile>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = authStorage.getToken();
    if (!token) {
      setLoading(false);
      return;
    }
    api.me({ silent: true })
      .then((currentUser) => setUser(currentUser))
      .catch(() => {
        authStorage.clearToken();
        setUser(undefined);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (payload: LoginPayload) => {
    const response = await api.login(payload);
    authStorage.setToken(response.token);
    setUser(response.user);
    message.success('登录成功');
    return response.user;
  };

  const logout = async () => {
    try {
      await api.logout({ silent: true });
    } catch {
      // ignore logout failure
    }
    authStorage.clearToken();
    setUser(undefined);
  };

  return (
    <AuthContext.Provider value={{ user, loading, isAdmin: user?.role === 'ADMIN', login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}
