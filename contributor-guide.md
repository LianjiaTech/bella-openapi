# Bella OpenAPI 贡献者指南

中文 | [English](./contributor-guide_EN.md)

欢迎加入 Bella OpenAPI 项目！本指南旨在帮助您快速上手并参与到项目开发中。我们非常感谢您对项目的兴趣和贡献。

## 目录

- [项目概述](#项目概述)
- [准备工作](#准备工作)
- [开发流程](#开发流程)
- [提交 Issues](#提交-issues)
- [代码贡献](#代码贡献)
- [代码风格](#代码风格)
- [测试](#测试)
- [常见问题](#常见问题)
- [社区支持](#社区支持)
- [贡献者](#贡献者)

## 项目概述

Bella OpenAPI 是一个开源的 API 管理平台，专注于 API 设计、开发和管理。该项目旨在提供一个高效、易用的 API 生态系统，帮助开发者更轻松地构建和维护 API。

## 准备工作

在开始贡献代码前，请确保您已经完成以下步骤：

1. **了解项目**：
   - 阅读 [技术文档](https://doc.bella.top/docs/bella-openapi/tech/metadata) 了解核心概念和技术细节
   - 使用 [DeepWiki](https://deepwiki.com/LianjiaTech/bella-openapi) 分析项目结构和代码库
   - 查看 [启动和部署指南](https://doc.bella.top/docs/bella-openapi/startup-deployment-details) 了解如何在本地设置开发环境

2. **环境配置**：
   - 确保您已安装必要的开发工具和依赖
   - 按照启动指南在本地成功运行项目，文档中只给出了docker启动方式，开发者可以根据配置详情，补充所需配置，自行启动项目

## 开发流程

1. **Fork 项目仓库**：在 GitHub 上 fork [Bella OpenAPI 仓库](https://github.com/LianjiaTech/bella-openapi)

2. **克隆仓库**：
   ```bash
   git clone https://github.com/YOUR-USERNAME/bella-openapi.git
   cd bella-openapi
   ```

3. **安装依赖**：
   前端：
   ```bash
   cd web
   npm install
   ```
   后端：
   ```bash
   cd api
   mvn install -DskipTests
   ```

4. **创建分支**：
   ```bash
   git checkout -b feature/your-feature-name
   ```

5. **开发**：进行代码修改和功能开发，并进行DEBUG

6. **测试**：确保您的代码通过所有测试

7. **提交更改**：
   ```bash
   git commit -m "描述您的更改"
   ```

8. **推送到 GitHub**：
   ```bash
   git push origin feature/your-feature-name
   ```

9. **创建 Pull Request**：在 GitHub 上创建 PR 到原始仓库

## 提交 Issues

我们使用 GitHub Issues 来追踪问题和功能请求。提交 Issue 时：

1. 使用清晰的标题描述问题或需求
2. 提供详细的描述，包括如何复现问题或功能的具体需求
3. 如可能，附上截图或相关代码
4. 为 Issue 设置适当的标签

**特别提示**：Bella OpenAPI 项目配备了智能 `bella-issues-bot`，它会自动处理提交的 Issues：
- 对于问题类 Issues：bot 会自动解答
- 对于需求类 Issues：bot 会分析需求并可能自动实现代码，然后推送到对应的分支

您可以在 [bella-issues-bot 仓库](https://github.com/bella-top/bella-issues-bot) 了解更多关于这个自动化工具的信息。

## 代码贡献

### Pull Request 流程

1. 确保您的 PR 只包含一个明确的变更或功能
2. 更新相关文档
3. 添加必要的测试
4. 确保所有测试通过
5. 提交 PR 时提供详细描述

### 代码审查

所有提交的 PR 将经过代码审查。请及时响应审查者的反馈和建议。

## 代码风格

请遵循项目现有的代码风格和最佳实践：

1. 使用一致的命名约定
2. 编写清晰的注释
3. 遵循模块化设计原则
4. 避免过度复杂的代码
5. 编写可测试的代码

## 测试

在提交代码前，请确保：

1. 添加适当的单元测试
2. 确保所有测试通过
3. 对于 UI 变更，进行必要的视觉和功能测试

## 常见问题

### 如何处理依赖冲突？

如果遇到依赖冲突，尝试以下步骤：
1. 清除本地依赖缓存
2. 确保使用项目推荐的依赖版本
3. 检查 `package.json` 中的版本约束

### 如何调试常见问题？

1. 使用浏览器开发工具调试前端问题
2. 检查服务器日志了解后端错误
3. 使用断点调试代码执行流程

### 开发环境无法启动？

1. 确认所有必要服务都已启动
2. 检查配置文件是否正确
3. 验证端口是否被占用
4. 查看错误日志获取详细信息

## 社区支持

我们鼓励积极参与社区讨论和互动：

1. GitHub Discussions 用于一般讨论和问题
2. Issues 用于具体的问题报告和功能请求
3. Pull Requests 用于代码贡献

## 贡献者

我们衷心感谢所有为 Bella OpenAPI 项目做出贡献的开发者！

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/szl97">
        <img src="https://github.com/szl97.png" width="100px;" alt="szl97"/>
        <br />
        <sub><b>szl97</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/nil4u">
        <img src="https://github.com/nil4u.png" width="100px;" alt="nil4u"/>
        <br />
        <sub><b>nil</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Jiakaic">
        <img src="https://github.com/Jiakaic.png" width="100px;" alt="Jiakaic"/>
        <br />
        <sub><b>Jiakaic</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/chlsmile">
        <img src="https://github.com/chlsmile.png" width="100px;" alt="chlsmile"/>
        <br />
        <sub><b>chlsmile</b></sub>
      </a>
    </td>
     <td align="center">
      <a href="https://github.com/shenenqing">
        <img src="https://github.com/shenenqing.png" width="100px;" alt="shenenqing"/>
        <br />
        <sub><b>shenenqing</b></sub>
      </a>
    </td>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/Rheasilvia">
        <img src="https://github.com/Rheasilvia.png" width="100px;" alt="Rheasilvia"/>
        <br />
        <sub><b>Mengjie Chen</b></sub>
      </a>
    </td>
  </tr>
</table>

### 如何加入贡献者列表

当您的 Pull Request 被合并后，项目维护者会将您添加到贡献者列表中。如果您已经贡献但未被列出，请通过 Issue 或 PR 提醒我们。

---

感谢您为 Bella OpenAPI 做出贡献！如有任何问题，请随时联系项目维护者或在 GitHub 上提出 Issue。
