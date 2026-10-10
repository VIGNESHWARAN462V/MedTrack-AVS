import { Client } from '@stomp/stompjs';
import { TOKEN_STORAGE_KEY } from '../context/AuthContext';
import { getWebSocketBrokerUrl } from './apiConfig';

/**
 * Connection states for UI indicators
 */
export const SocketStatus = {
  CONNECTED: 'CONNECTED',
  CONNECTING: 'CONNECTING',
  RECONNECTING: 'RECONNECTING',
  DISCONNECTED: 'DISCONNECTED',
  ERROR: 'ERROR',
};

class TrackingSocketService {
  constructor() {
    this.client = null;
    this.status = SocketStatus.DISCONNECTED;
    this.statusListeners = new Set();
    this.activeSubscriptions = new Map(); // topic -> { callback, stompSub }
    this.reconnectAttempts = 0;
  }

  /**
   * Determine authoritative WebSocket/STOMP broker URL based on configured backend.
   */
  getBrokerUrl() {
    return getWebSocketBrokerUrl();
  }

  /**
   * Notify all registered UI listeners of connection state change.
   */
  setStatus(newStatus) {
    if (this.status !== newStatus) {
      this.status = newStatus;
      this.statusListeners.forEach((listener) => {
        try {
          listener(newStatus);
        } catch (e) {
          console.error('[WebSocket] Status listener error:', e);
        }
      });
    }
  }

  /**
   * Subscribe a React component to connection status updates.
   * Returns an unregister function.
   */
  onStatusChange(listener) {
    this.statusListeners.add(listener);
    listener(this.status);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  /**
   * Connect to STOMP broker using existing JWT token.
   */
  connect(token) {
    const authToken =
      token ||
      (typeof localStorage !== 'undefined'
        ? localStorage.getItem(TOKEN_STORAGE_KEY) || localStorage.getItem('token')
        : null);

    if (!authToken) {
      console.warn('[WebSocket] Cannot connect: missing JWT authentication token');
      this.setStatus(SocketStatus.DISCONNECTED);
      return;
    }

    if (this.client && this.client.active) {
      return;
    }

    this.setStatus(SocketStatus.CONNECTING);

    const brokerURL = this.getBrokerUrl();

    this.client = new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${authToken}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: (msg) => {
        if (import.meta.env?.DEV && !msg.includes('PING') && !msg.includes('PONG')) {
          console.debug('[STOMP]', msg);
        }
      },
      onConnect: (frame) => {
        console.log('[WebSocket] STOMP connected successfully:', frame);
        this.reconnectAttempts = 0;
        this.setStatus(SocketStatus.CONNECTED);

        // Resubscribe any registered topics
        this.resubscribeAll();
      },
      onStompError: (frame) => {
        console.error('[WebSocket] Broker error:', frame.headers?.message, frame.body);
        this.setStatus(SocketStatus.ERROR);
      },
      onWebSocketError: (event) => {
        console.error('[WebSocket] Transport error:', event);
        this.setStatus(SocketStatus.ERROR);
      },
      onWebSocketClose: (event) => {
        console.warn('[WebSocket] Connection closed:', event.code, event.reason);
        if (this.status === SocketStatus.CONNECTED) {
          this.setStatus(SocketStatus.RECONNECTING);
        } else if (this.status !== SocketStatus.ERROR) {
          this.setStatus(SocketStatus.DISCONNECTED);
        }
      },
    });

    this.client.activate();
  }

  /**
   * Re-establish subscriptions upon successful connection or reconnect.
   */
  resubscribeAll() {
    if (!this.client || !this.client.connected) return;

    this.activeSubscriptions.forEach((entry, topic) => {
      if (entry.stompSub) {
        try {
          entry.stompSub.unsubscribe();
        } catch {
          // ignore
        }
        entry.stompSub = null;
      }
      if (entry.callbacks && entry.callbacks.size > 0) {
        entry.stompSub = this.client.subscribe(topic, (message) => {
          try {
            const payload = JSON.parse(message.body);
            entry.callbacks.forEach((cb) => {
              try {
                cb(payload);
              } catch (err) {
                console.error(`[WebSocket] Callback error on ${topic}:`, err);
              }
            });
          } catch (e) {
            console.error(`[WebSocket] Failed to parse message on ${topic}:`, e);
          }
        });
        console.log(`[WebSocket] Resubscribed to ${topic} (${entry.callbacks.size} listeners)`);
      }
    });
  }

  /**
   * Subscribe to global asset tracking updates: /topic/tracking/assets
   */
  subscribeToAssets(callback) {
    return this.subscribe('/topic/tracking/assets', callback);
  }

  /**
   * Subscribe to a specific asset's updates: /topic/tracking/assets/{assetId}
   */
  subscribeToAsset(assetId, callback) {
    return this.subscribe(`/topic/tracking/assets/${assetId}`, callback);
  }

  /**
   * Subscribe to global alert notifications: /topic/alerts
   */
  subscribeToAlerts(callback) {
    return this.subscribe('/topic/alerts', callback);
  }

  /**
   * Subscribe to a specific asset's alert notifications: /topic/alerts/{assetId}
   */
  subscribeToAssetAlerts(assetId, callback) {
    return this.subscribe(`/topic/alerts/${assetId}`, callback);
  }

  /**
   * Subscribe to global asset transfer notifications: /topic/transfers
   */
  subscribeToTransfers(callback) {
    return this.subscribe('/topic/transfers', callback);
  }

  /**
   * Subscribe to a specific asset's transfer notifications: /topic/transfers/{assetId}
   */
  subscribeToAssetTransfers(assetId, callback) {
    return this.subscribe(`/topic/transfers/${assetId}`, callback);
  }

  /**
   * Generic STOMP subscription helper supporting multiple listeners per topic.
   */
  subscribe(topic, callback) {
    if (!this.activeSubscriptions.has(topic)) {
      this.activeSubscriptions.set(topic, {
        callbacks: new Set(),
        stompSub: null,
      });
    }

    const entry = this.activeSubscriptions.get(topic);
    entry.callbacks.add(callback);

    if (this.client && this.client.connected && !entry.stompSub) {
      entry.stompSub = this.client.subscribe(topic, (message) => {
        try {
          const payload = JSON.parse(message.body);
          entry.callbacks.forEach((cb) => {
            try {
              cb(payload);
            } catch (err) {
              console.error(`[WebSocket] Callback error on ${topic}:`, err);
            }
          });
        } catch (e) {
          console.error(`[WebSocket] Failed to parse message on ${topic}:`, e);
        }
      });
      console.log(`[WebSocket] Subscribed to ${topic}`);
    }

    // Return unsubscription cleanup for this listener
    return () => {
      const currentEntry = this.activeSubscriptions.get(topic);
      if (currentEntry) {
        currentEntry.callbacks.delete(callback);
        if (currentEntry.callbacks.size === 0) {
          if (currentEntry.stompSub) {
            try {
              currentEntry.stompSub.unsubscribe();
            } catch {
              // ignore
            }
          }
          this.activeSubscriptions.delete(topic);
          console.log(`[WebSocket] Unsubscribed from ${topic}`);
        }
      }
    };
  }

  /**
   * Disconnect STOMP client cleanly.
   */
  disconnect() {
    if (this.client) {
      try {
        this.client.deactivate();
      } catch (e) {
        console.error('[WebSocket] Error during disconnect:', e);
      }
      this.client = null;
    }
    this.activeSubscriptions.clear();
    this.setStatus(SocketStatus.DISCONNECTED);
  }

  /**
   * Get current connection status.
   */
  getStatus() {
    return this.status;
  }
}

export const trackingSocketService = new TrackingSocketService();
export default trackingSocketService;
