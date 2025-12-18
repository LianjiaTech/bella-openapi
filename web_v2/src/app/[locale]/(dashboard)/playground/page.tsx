export default function PlaygroundPage() {
  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">API Playground</h1>
        <p className="text-gray-600">测试和调试 AI 模型 API</p>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              选择模型
            </label>
            <select className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
              <option>GPT-4</option>
              <option>GPT-3.5 Turbo</option>
              <option>Claude-3 Opus</option>
              <option>Gemini Pro</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              输入提示词
            </label>
            <textarea
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              rows={6}
              placeholder="请输入您的提示词..."
            />
          </div>

          <button className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 rounded-lg transition-colors">
            发送请求
          </button>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              响应结果
            </label>
            <div className="w-full px-4 py-3 border border-gray-300 rounded-lg bg-gray-50 min-h-[200px] text-gray-600">
              响应内容将显示在这里...
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
