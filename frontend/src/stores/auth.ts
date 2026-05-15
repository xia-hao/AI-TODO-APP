import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'
import type { User } from '@/types'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<User | null>(null)

  async function login(email: string, password: string) {
    const { data } = await authApi.login({ email, password })
    token.value = data.data.accessToken
    user.value = data.data.user
    localStorage.setItem('token', token.value)
  }

  async function register(username: string, email: string, password: string, displayName?: string) {
    const { data } = await authApi.register({ username, email, password, displayName })
    token.value = data.data.accessToken
    user.value = data.data.user
    localStorage.setItem('token', token.value)
  }

  async function fetchMe() {
    const { data } = await authApi.me()
    user.value = data.data
  }

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
  }

  // ---- Token refresh ----

  let _refreshPromise: Promise<string | null> | null = null

  /** 刷新 token，自动去重 — 多个并发 401 共享同一个刷新请求 */
  async function refreshToken(): Promise<string | null> {
    if (_refreshPromise) return _refreshPromise
    _refreshPromise = _doRefresh()
    try {
      return await _refreshPromise
    } finally {
      _refreshPromise = null
    }
  }

  async function _doRefresh(): Promise<string | null> {
    try {
      const { data } = await axios.post('/api/auth/refresh', {}, { withCredentials: true })
      const newToken = data.data.accessToken
      setToken(newToken)
      return newToken
    } catch {
      logout()
      return null
    }
  }

  /** 获取有效 token，如果即将过期则自动刷新 */
  async function getFreshToken(): Promise<string | null> {
    const t = token.value
    if (!t) return null
    try {
      const payload = JSON.parse(atob(t.split('.')[1]))
      const exp = payload.exp
      if (exp && exp * 1000 > Date.now() + 60_000) return t
    } catch {
      // 不是 JWT 格式，直接返回
      return t
    }
    return refreshToken()
  }

  return { token, user, login, register, fetchMe, setToken, logout, refreshToken, getFreshToken }
})
