import React, { useEffect } from 'react';
import Layout from '@theme/Layout';
import BrowserOnly from '@docusaurus/BrowserOnly';
import useBaseUrl from '@docusaurus/useBaseUrl';
import { translate } from '@docusaurus/Translate';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import '../css/redoc-overrides.css'; // 引入自定义CSS样式

export default function ApiViewerPage() {
  const { i18n } = useDocusaurusContext();
  const currentLocale = i18n.currentLocale;
  
  // 根据当前语言选择正确的API规范文件
  // 如果找不到当前语言的API规范，则回退到默认中文版本
  const specUrl = useBaseUrl(
    currentLocale === 'en' ? '/openapi/openapi-en.json' : '/openapi/openapi.json'
  );
  
  return (
    <Layout
      title={translate({
        id: 'pages.apiViewer.title',
        message: 'API 文档',
      })}
      description={translate({
        id: 'pages.apiViewer.description',
        message: 'Bella OpenAPI 完整 API 文档',
      })}
      noFooter={true}>
      <main className="container" style={{padding: 0, maxWidth: '100%', height: 'calc(100vh - 60px)'}}> {/* 调整高度以填充整个视口 */}
        {/* 加载指示器 */}
        <div id="redoc-loading" style={{
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center',
          height: '100%', // 改为100%填充容器
          fontSize: '1.2rem',
          color: '#666'
        }}>
          API 文档加载中...
        </div>
        
        {/* 使用 script 标签直接加载 Redoc */}
        <BrowserOnly>
          {() => {
            useEffect(() => {
              // 使用最直接的方式加载 Redoc
              const loadRedoc = async () => {
                try {
                  // 隐藏加载指示器
                  const loadingEl = document.getElementById('redoc-loading');
                  if (loadingEl) {
                    loadingEl.style.display = 'none';
                  }
                  
                  // 创建 script 标签
                  const script = document.createElement('script');
                  script.src = 'https://cdn.jsdelivr.net/npm/redoc@2.0.0/bundles/redoc.standalone.js';
                  script.async = true;
                  script.onload = () => {
                    // 当 Redoc 加载成功后，初始化它
                    // @ts-ignore
                    window.Redoc.init(
                      specUrl,
                      {
                        scrollYOffset: 60, // 增大以避免导航栏遮挡
                        hideHostname: false,
                        expandResponses: '200,201',
                        nativeScrollbars: true,
                        theme: {
                          colors: {
                            primary: { main: '#1890ff' },
                            // 其余主题配置保持不变
                          },
                          typography: {
                            fontSize: '16px',
                            headings: {
                              fontFamily: '"Source Sans Pro", sans-serif',
                            },
                            fontFamily: 'Montserrat, Helvetica, Arial, sans-serif',
                          },
                          sidebar: {
                            width: '300px',
                          },
                        },
                      },
                      document.getElementById('redoc-container')
                    );
                  };
                  script.onerror = () => {
                    console.error('Failed to load Redoc script');
                    const errorEl = document.getElementById('redoc-loading');
                    if (errorEl) {
                      errorEl.textContent = '加载 API 文档组件失败，请刷新页面重试';
                      errorEl.style.color = 'red';
                      errorEl.style.display = 'flex';
                    }
                  };
                  document.body.appendChild(script);
                } catch (error) {
                  console.error('Error setting up Redoc:', error);
                  const errorEl = document.getElementById('redoc-loading');
                  if (errorEl) {
                    errorEl.textContent = '加载 API 文档组件失败，请刷新页面重试';
                    errorEl.style.color = 'red';
                    errorEl.style.display = 'flex';
                  }
                }
              };
              
              loadRedoc();
              
              // 清理函数
              return () => {
                const scriptEl = document.querySelector('script[src="https://cdn.jsdelivr.net/npm/redoc@2.0.0/bundles/redoc.standalone.js"]');
                if (scriptEl) {
                  document.body.removeChild(scriptEl);
                }
              };
            }, []);
            
            return <div id="redoc-container" style={{ height: '100%' }}></div>; {/* 调整高度以填充整器 */}
          }}
        </BrowserOnly>
      </main>
    </Layout>
  );
}