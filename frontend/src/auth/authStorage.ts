const TOKEN_KEY = 'huatuo_dx_auth_token';

export const authStorage = {
  getToken() {
    return window.localStorage.getItem(TOKEN_KEY) || '';
  },
  setToken(token: string) {
    window.localStorage.setItem(TOKEN_KEY, token);
  },
  clearToken() {
    window.localStorage.removeItem(TOKEN_KEY);
  },
};
