class DevTools {
  constructor(socket, timeoutMs = 15000) {
    this.socket = socket;
    this.timeoutMs = timeoutMs;
    this.connectionError = null;
    this.nextId = 1;
    this.pending = new Map();
    this.events = new Map();
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.id) {
        const pending = this.pending.get(message.id);
        if (!pending) return;
        this.pending.delete(message.id);
        if (message.error) pending.reject(new Error(message.error.message));
        else pending.resolve(message.result);
        return;
      }
      const listeners = this.events.get(message.method) || [];
      for (const listener of listeners) listener(message.params || {});
    };
    // Chrome can close the connection before acknowledging Browser.close. Settle requests
    // so the caller reaches process termination and temporary-profile cleanup either way.
    socket.onclose = () => this.failConnection(new Error('DevTools connection closed'));
    socket.onerror = () => this.failConnection(new Error('DevTools connection error'));
  }

  failConnection(error) {
    this.connectionError = error;
    for (const pending of this.pending.values()) pending.reject(error);
  }

  send(method, params = {}) {
    if (this.connectionError) return Promise.reject(this.connectionError);
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const finish = (error, result) => {
        clearTimeout(timeout);
        this.pending.delete(id);
        if (error) reject(error);
        else resolve(result);
      };
      const timeout = setTimeout(() => finish(new Error(`Timed out waiting for DevTools ${method}`)), this.timeoutMs);
      this.pending.set(id, {
        resolve: result => finish(null, result),
        reject: error => finish(error),
      });
      try {
        this.socket.send(JSON.stringify({ id, method, params }));
      } catch (error) {
        finish(error);
      }
    });
  }

  on(method, listener) {
    const listeners = this.events.get(method) || [];
    listeners.push(listener);
    this.events.set(method, listeners);
  }
}

module.exports = { DevTools };
