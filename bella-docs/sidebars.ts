import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  documentationSidebar: [
    {
      type: 'doc',
      id: 'intro',
      label: '介绍',
    },
    {
      type: 'category',
      label: '配置与部署 (Configuration & Deployment)',
      items: [
        'configuration-details', // docs/configuration-details.md
        'startup-deployment-details', // docs/startup-deployment-details.md
      ],
    },
    {
      type: 'category',
      label: '核心功能 (Core Features)',
      items: [
        'core/chat-completions',
        'core/embeddings',
        'core/flash-asr',
        'core/tts',
        'core/realtime',
      ],
    },
    {
      type: 'category',
      label: '技术文档（Tech Documents）',
      items: [
        'tech/system-structure',
        'tech/metadata',
        'tech/dynamic-route',
        'tech/function-call',
        'tech/async-performace',
        'tech/user-authorization',
        'tech/usage-manage'
      ],
    },
  ],
};

export default sidebars;