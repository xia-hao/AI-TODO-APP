import { useDark, useToggle } from '@vueuse/core'

const isDark = useDark({
  storageKey: 'todo-theme',
  valueDark: 'dark',
  valueLight: 'light'
})

const toggleDark = useToggle(isDark)

export function useTheme() {
  return { isDark, toggleDark }
}
