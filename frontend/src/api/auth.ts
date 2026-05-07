import request from '@/utils/request'
import type { LoginRequest, LoginResponse, RegisterRequest, UserInfo } from '@/types/auth'

/**
 * 登录接口
 */
export function login(data: LoginRequest) {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/**
 * 手机号登录接口
 */
export function phoneLogin(phone: string, code: string) {
  return request<LoginResponse>({
    url: '/auth/phone-login',
    method: 'post',
    params: { phone, code }
  })
}

/**
 * 注册接口
 */
export function register(data: RegisterRequest) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}

/**
 * 登出接口
 */
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

/**
 * 获取用户信息接口
 */
export function getUserInfo() {
  return request<UserInfo>({
    url: '/auth/info',
    method: 'get'
  })
}

/**
 * 密码重置接口
 */
export function resetPassword(data: { phone: string; code: string; newPassword: string; confirmPassword: string }) {
  return request({
    url: '/auth/reset-password',
    method: 'post',
    data
  })
}
