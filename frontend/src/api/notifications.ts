import http from './http';

export const notificationsApi = {
  unread: () => http.get('/notifications/unread'),
  unreadCount: () => http.get('/notifications/unread-count'),
  markRead: (id: number) => http.put(`/notifications/${id}/read`),
  markAllRead: () => http.put('/notifications/read-all'),
};