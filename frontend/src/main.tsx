import React from 'react';
import ReactDOM from 'react-dom/client';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { HashRouter } from 'react-router-dom';
import RootApp from './App';
import { AuthProvider } from './auth/AuthContext';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#ff7a59',
          colorInfo: '#1d7af3',
          borderRadius: 16,
          fontFamily: '"Segoe UI", "PingFang SC", sans-serif',
        },
      }}
    >
      <App>
        <AuthProvider>
          <HashRouter>
            <RootApp />
          </HashRouter>
        </AuthProvider>
      </App>
    </ConfigProvider>
  </React.StrictMode>,
);
