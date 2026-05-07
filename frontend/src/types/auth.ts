// 用户信息类型
export interface UserInfo {
  id: number
  username: string
  email?: string
  phone?: string
  role?: string
  status: number
  enabled: boolean
  createdAt: string
  updatedAt: string
}

// 登录请求
export interface LoginRequest {
  username: string
  password: string
  rememberMe?: boolean
}

// 登录响应
export interface LoginResponse {
  token: string
  username: string
  role?: string
  expiresIn: number
  userInfo?: UserInfo
}

// 注册请求
export interface RegisterRequest {
  username: string
  password: string
  confirmPassword: string
  email?: string
  phone?: string
  code: string
}

// 密码重置请求
export interface ResetPasswordRequest {
  phone: string
  code: string
  newPassword: string
  confirmPassword: string
}

// 验证码请求
export interface VerifyCodeRequest {
  phone: string
  type: 'register' | 'login' | 'reset'
}

// 验证码响应
export interface VerifyCodeResponse {
  expireIn: number
  countdown: number
}
