// GitHub API client functions

interface TokenResponse {
    token: string;
  }
  
  interface StarStatusResponse {
    isStarred: boolean;
  }
  
  interface GitHubConfigResponse {
    clientId: string;
  }
  
  /**
   * Get GitHub OAuth Client ID from server configuration
   */
  export async function getGitHubConfig(): Promise<GitHubConfigResponse> {
    try {
      const response = await fetch('/api/github/config');
  
      if (!response.ok) {
        throw new Error('GitHub配置未正确设置');
      }
  
      const data = await response.json();
      return data;
    } catch (error) {
      console.error('获取GitHub配置失败:', error);
      throw error;
    }
  }
  
  /**
   * Exchange OAuth authorization code for access token
   */
  export async function exchangeOAuthCode(code: string): Promise<TokenResponse> {
    try {
      const response = await fetch('/api/github/oauth', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ code }),
      });
  
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || '获取token失败');
      }
  
      const data = await response.json();
      return data;
    } catch (error) {
      console.error('Token交换失败:', error);
      throw error;
    }
  }
  
  /**
   * Check if the user has starred the repository
   */
  export async function checkStarStatus(token: string): Promise<StarStatusResponse> {
    try {
      const response = await fetch('/api/github/star', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
  
      if (!response.ok) {
        return { isStarred: false };
      }
  
      const data = await response.json();
      return data;
    } catch (error) {
      console.error('检查星标状态失败:', error);
      return { isStarred: false };
    }
  }
  
  /**
   * Star the repository
   */
  export async function starRepository(token: string): Promise<void> {
    try {
      const response = await fetch('/api/github/star', {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
  
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || '点赞操作失败');
      }
    } catch (error) {
      console.error('Star操作失败:', error);
      throw error;
    }
  }
  