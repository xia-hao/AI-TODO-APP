<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2>{{ $t('auth.register') }}</h2>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="$t('auth.usernamePlaceholder')" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="displayName">
          <el-input v-model="form.displayName" :placeholder="$t('auth.displayNamePlaceholder')" prefix-icon="UserFilled" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" :placeholder="$t('auth.emailPlaceholder')" type="email" prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" :placeholder="$t('auth.passwordMinPlaceholder')" type="password" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">{{ $t('auth.register') }}</el-button>
      </el-form>
      <p class="switch-link">{{ $t('auth.hasAccount') }}<router-link to="/login">{{ $t('auth.loginNow') }}</router-link></p>
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
const form = ref({ username: '', displayName: '', email: '', password: '' })

const rules = {
  username: [{ required: true, min: 2, max: 50, message: t('auth.usernameValidate'), trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: t('auth.emailInvalid'), trigger: 'blur' }],
  password: [{ required: true, min: 6, message: t('auth.passwordMin'), trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.register(form.value.username, form.value.email, form.value.password, form.value.displayName || undefined)
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('auth.registerFailed'))
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
