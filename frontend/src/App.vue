<template>
  <router-view />
</template>

<style>
/* 全局禁止文本选中，排除输入框等 */
*:not(input):not(textarea):not([contenteditable="true"]) {
  user-select: none !important;
  -webkit-user-select: none !important;  /* Safari */
  -moz-user-select: none !important;
  -ms-user-select: none !important;
}

/* 确保输入框内可以正常选中 */
input, textarea, [contenteditable="true"] {
  user-select: text !important;
  -webkit-user-select: text !important;
}
</style>