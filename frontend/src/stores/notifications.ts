import { defineStore } from 'pinia';
import { ref } from 'vue';
import { connectWebSocket } from '@/services/websocket';
import { notificationsApi } from '@/api/notifications';

export const useNotificationsStore = defineStore('notifications', () => {
  const unreadCount = ref(0);
  const list = ref<any[]>([]);

  function addNotification(notification: any) {
    list.value.unshift(notification);
    unreadCount.value++;
  }

  async function fetchUnread() {
    try {
      const { data } = await notificationsApi.unread();
      list.value = data.data;
      unreadCount.value = list.value.length;
    } catch (e) { /* ignore */ }
  }

  async function markRead(id: number) {
    await notificationsApi.markRead(id);
    list.value = list.value.filter(n => n.id !== id);
    unreadCount.value = list.value.length;
  }

  async function markAllRead() {
    await notificationsApi.markAllRead();
    list.value = [];
    unreadCount.value = 0;
  }

  function initWebSocket() {
    connectWebSocket((notification) => {
      addNotification(notification);
      // 可播放提示音或弹出通知
    });
  }

  return { unreadCount, list, fetchUnread, markRead, markAllRead, initWebSocket };
});