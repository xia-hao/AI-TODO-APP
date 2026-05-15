import axios from 'axios'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'
import i18n from '@/i18n'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use(config => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

let refreshing = false
let queue: Array<(token: string | null) => void> = []

function flushQueue(token: string | null) {
  queue.forEach(cb => cb(token))
  queue = []
}

http.interceptors.response.use(
  res => res,
  async err => {
    const original = err.config
    if (err.response?.status === 401 && !original._retry && !original.url?.includes('/auth/')) {
      if (refreshing) {
        return new Promise((resolve, reject) => {
          queue.push(token => {
            if (!token) return reject(err)
            original.headers.Authorization = `Bearer ${token}`
            resolve(http(original))
          })
        })
      }
      original._retry = true
      refreshing = true
      try {
        const newToken = await useAuthStore().refreshToken()
        if (!newToken) throw err
        flushQueue(newToken)
        original.headers.Authorization = `Bearer ${newToken}`
        return http(original)
      } catch {
        flushQueue(null)
        ElMessageBox.alert(
          i18n.global.t('app.loginExpired'),
          i18n.global.t('app.hint'),
          {
            confirmButtonText: i18n.global.t('app.goLogin'),
            showClose: false,
            type: 'warning',
          },
        ).finally(() => router.push('/login'))
        return Promise.reject(err)
      } finally {
        refreshing = false
      }
    }
    return Promise.reject(err)
  }
)

export default http
