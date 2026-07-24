# ChatInput 组件单元测试

## 📋 测试覆盖范围

### 1. fileUpload.test.tsx - 文件上传功能测试
测试图片和视频上传的核心功能:
- ✅ 文件选择触发
- ✅ 文件类型验证 (仅支持 image/* 和 video/*)
- ✅ 文件大小限制 (图片 10MB, 视频 50MB)
- ✅ base64 转换正确性
- ✅ 数据格式符合 `ContentPart[]`
- ✅ input.value 重置

**测试用例数: 9 个**

### 2. dataTransform.test.tsx - 数据转换逻辑测试
测试各种数据转换场景:
- ✅ string → ContentPart[] 转换
- ✅ 保留原有文本内容
- ✅ 追加多个媒体文件
- ✅ 混合场景 (文本 + 图片 + 视频)
- ✅ 边界情况 (空字符串, 空数组, 纯空格)

**测试用例数: 7 个**

### 3. index.test.tsx - 组件集成测试
测试组件整体功能和交互:
- ✅ 组件渲染
- ✅ hasContent 逻辑判断
- ✅ 与 Textarea 输入兼容性
- ✅ 发送功能
- ✅ Streaming 状态处理
- ✅ 禁用状态

**测试用例数: 18 个**

---

## 🛠️ Mock 工具

### mocks/files.ts
提供各种测试文件:
- `mockImageFile()` - 标准图片 (1MB)
- `mockVideoFile()` - 标准视频 (5MB)
- `mockLargeImageFile()` - 超大图片 (15MB)
- `mockLargeVideoFile()` - 超大视频 (60MB)
- `mockInvalidFile()` - 不支持的文件 (PDF)

### mocks/controller.ts
提供各种 controller 状态:
- `createMockController()` - 基础 controller
- `createControllerWithText()` - 带文本的 controller
- `createControllerWithContent()` - 带 ContentPart[] 的 controller
- `createStreamingController()` - streaming 状态的 controller

### mocks/fileReader.ts
Mock FileReader API:
- `mockFileReader()` - 模拟成功读取
- `mockFileReaderError()` - 模拟读取失败

---

## 🚀 运行测试

### 运行所有测试
```bash
npm run test
```

### 运行特定测试文件
```bash
# 文件上传功能测试
npm run test fileUpload.test.tsx

# 数据转换逻辑测试
npm run test dataTransform.test.tsx

# 组件集成测试
npm run test index.test.tsx
```

### 生成覆盖率报告
```bash
npm run test -- --coverage
```

### Watch 模式 (开发时使用)
```bash
npm run test -- --watch
```

---

## 📊 测试覆盖率目标

| 指标 | 目标 | 说明 |
|------|------|------|
| 语句覆盖率 | ≥ 85% | Statements |
| 分支覆盖率 | ≥ 80% | Branches |
| 函数覆盖率 | ≥ 90% | Functions |
| 行覆盖率 | ≥ 85% | Lines |

### 重点覆盖函数
- `handleFileUpload()` - 100%
- `handleFileChange()` - 100%
- 文件类型验证逻辑 - 100%
- 文件大小验证逻辑 - 100%
- 数据转换逻辑 - 100%

---

## 📝 测试统计

- **总测试用例数**: 34 个
- **Mock 工具函数**: 12 个
- **测试文件数**: 3 个
- **覆盖的功能点**: 20+ 个

---

## 🔍 测试策略

### 单元测试 (Unit Tests)
- 测试独立函数和逻辑
- Mock 外部依赖 (FileReader, DOM API)
- 快速执行, 高覆盖率

### 集成测试 (Integration Tests)
- 测试组件整体行为
- 验证功能协同工作
- 模拟真实用户交互

### 边界测试 (Edge Cases)
- 文件过大
- 文件类型错误
- 空输入
- 取消选择

---

## ⚠️ 注意事项

1. **FileReader Mock**: 所有测试使用 Mock FileReader, 避免真实文件 I/O
2. **异步操作**: 使用 `waitFor()` 等待异步操作完成
3. **清理工作**: 每个测试后清理 Mock 和 Jest 调用记录
4. **DOM 查询**: 优先使用 Testing Library 的查询方法

---

## 🐛 调试技巧

### 查看测试输出
```bash
npm run test -- --verbose
```

### 仅运行失败的测试
```bash
npm run test -- --onlyFailures
```

### 调试单个测试
在测试用例前添加 `.only`:
```typescript
test.only('1. 点击"添加图片"按钮...', () => {
  // ...
});
```

---

## 📚 相关文档

- [Testing Library 文档](https://testing-library.com/docs/react-testing-library/intro/)
- [Jest 文档](https://jestjs.io/docs/getting-started)
- [User Event 文档](https://testing-library.com/docs/user-event/intro/)
