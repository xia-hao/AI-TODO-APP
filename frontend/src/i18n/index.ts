import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN.js'
import enUS from './locales/en-US.js'

const savedLang = localStorage.getItem('todo-lang') || 'zh-CN'

const i18n = createI18n({
  legacy: false,
  locale: savedLang,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})

export function setLanguage(lang: string) {
  i18n.global.locale.value = lang as any
  localStorage.setItem('todo-lang', lang)
}

export default i18n
