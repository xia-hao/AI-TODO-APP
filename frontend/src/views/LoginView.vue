<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2>{{ $t('auth.login') }}</h2>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="submit">
        <el-form-item prop="email">
          <el-input v-model="form.email" :placeholder="$t('auth.emailPlaceholder')" type="email" prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" :placeholder="$t('auth.passwordPlaceholder')" type="password" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">{{ $t('auth.login') }}</el-button>
      </el-form>
      <p class="switch-link">{{ $t('auth.noAccount') }}<router-link to="/register">{{ $t('auth.registerNow') }}</router-link></p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()
const formRef = ref()
const loading = ref(false)
const form = ref({ email: '', password: '' })

const rules = {
  email: [{ required: true, type: 'email', message: t('auth.emailInvalid'), trigger: 'blur' }],
  password: [{ required: true, message: t('auth.passwordRequired'), trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form.value.email, form.value.password)
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('auth.loginFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--el-bg-color-page); }
.auth-card { width: 380px; }
.auth-card h2 { text-align: center; margin-bottom: 24px; color: var(--el-text-color-primary); }
.switch-link { text-align: center; margin-top: 16px; color: var(--el-text-color-secondary); font-size: 14px; }
</style>
