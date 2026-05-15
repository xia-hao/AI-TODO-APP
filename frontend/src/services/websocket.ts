import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuthStore } from '@/stores/auth';

interface Subscription {
  destination: string;
  callback: (msg: any) => void;
  stompSub: any;
}

let client: Client | null = null;
const subscriptions: Map<string, Subscription[]> = new Map();

export function connectWebSocket(onNotification: (msg: any) => void) {
  const auth = useAuthStore();
  if (!auth.token) return;

  if (client?.active) {
    client.deactivate();
  }

  client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    connectHeaders: { Authorization: 'Bearer ' + auth.token },
    reconnectDelay: 5000,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    connectionTimeout: 10000,

    onConnect: () => {
      // 通知订阅
      try {
        client?.subscribe(`/user/queue/notifications`, (message: IMessage) => {
          const notification = JSON.parse(message.body);
          onNotification(notification);
        });
      } catch (e) {
        console.error('[STOMP] 订阅通知失败', e);
      }

      // 重连后重新订阅所有已注册的 topic
      subscriptions.forEach((subs, destination) => {
        subs.forEach(sub => {
          try {
            const stompSub = client?.subscribe(destination, (message: IMessage) => {
              const payload = JSON.parse(message.body);
              sub.callback(payload);
            });
            if (stompSub) sub.stompSub = stompSub;
          } catch (e) {
            console.error(`[STOMP] 重新订阅 ${destination} 失败`, e);
          }
        });
      });
    },

    onStompError: (frame) => {
      console.error('[STOMP] 错误', frame);
    },

    onWebSocketClose: (evt) => {
      if (evt.code !== 1000) {
        console.warn('[STOMP] 连接意外关闭', evt.code, evt.reason);
      }
    },
  });

  client.activate();
}

/** 订阅项目变更主题，返回取消订阅函数 */
export function subscribeToProject(projectId: number, callback: (payload: any) => void) {
  const destination = `/topic/projects/${projectId}`;
  const entry: Subscription = {
    destination,
    callback,
    stompSub: null,
  };

  if (client?.active) {
    try {
      entry.stompSub = client.subscribe(destination, (message: IMessage) => {
        const payload = JSON.parse(message.body);
        callback(payload);
      });
    } catch (e) {
      console.error(`[STOMP] 订阅 ${destination} 失败`, e);
    }
  }

  // 存入列表，重连时自动恢复
  const subs = subscriptions.get(destination) || [];
  subs.push(entry);
  subscriptions.set(destination, subs);

  // 返回取消订阅函数
  return () => {
    if (entry.stompSub) {
      try { entry.stompSub.unsubscribe(); } catch { /* ignore */ }
    }
    const remaining = (subscriptions.get(destination) || []).filter(s => s !== entry);
    if (remaining.length === 0) {
      subscriptions.delete(destination);
    } else {
      subscriptions.set(destination, remaining);
    }
  };
}

export function disconnectWebSocket() {
  subscriptions.clear();
  if (client) {
    try { client.deactivate(); } catch { /* ignore */ }
    client = null;
  }
}
