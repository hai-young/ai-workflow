import request from '@/utils/request'
import type { VerifyCodeRequest, VerifyCodeResponse } from '@/types/auth'

/**
 * 发送验证码接口
 */
export function sendVerifyCode(data: VerifyCodeRequest) {
  return request<VerifyCodeResponse>({
    url: '/verify-code/send',
    method: 'post',
    data
  })
}

/**
 * 验证验证码接口
 */
export function verifyCode(phone: string, code: string, type: string) {
  return request({
    url: '/verify-code/verify',
    method: 'post',
    params: { phone, code, type }
  })
}
