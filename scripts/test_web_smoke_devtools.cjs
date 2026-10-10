const assert = require('node:assert/strict');
const { test } = require('node:test');
const { DevTools } = require('./web_smoke_devtools.cjs');

function connection(timeoutMs = 10) {
  const socket = {
    sent: [],
    send(data) { this.sent.push(JSON.parse(data)); },
    reply(message) { this.onmessage({ data: JSON.stringify(message) }); },
  };
  return { socket, client: new DevTools(socket, timeoutMs) };
}

// A broken transport must fail this assertion instead of leaving the test waiting forever.
async function failureWithinDeadline(request, expected) {
  let timer;
  const outcome = request.then(() => 'resolved', error => error.message);
  try {
    const result = await Promise.race([
      outcome,
      new Promise(resolve => { timer = setTimeout(() => resolve('request never settled'), 100); }),
    ]);
    assert.match(result, expected);
  } finally {
    clearTimeout(timer);
  }
}

test('normal replies and protocol errors settle and release requests', async () => {
  const { socket, client } = connection();
  const success = client.send('Browser.getVersion');
  socket.reply({ id: socket.sent[0].id, result: { product: 'Chrome' } });
  assert.deepEqual(await success, { product: 'Chrome' });
  const failure = client.send('Unknown.command');
  socket.reply({ id: socket.sent[1].id, error: { message: 'Unknown command' } });
  await assert.rejects(failure, /Unknown command/);
  assert.equal(client.pending.size, 0);
});

test('socket closure before Browser.close acknowledgement settles every request', async () => {
  const { socket, client } = connection();
  const closing = failureWithinDeadline(client.send('Browser.close'), /connection closed/);
  const pending = failureWithinDeadline(client.send('Runtime.evaluate'), /connection closed/);
  socket.onclose?.();
  await Promise.all([closing, pending]);
  assert.equal(client.pending.size, 0);
  await assert.rejects(client.send('Page.enable'), /connection closed/);
  assert.equal(socket.sent.length, 2);
});

test('an unresponsive connection times out with the command name', async () => {
  const { socket, client } = connection();
  await failureWithinDeadline(client.send('Browser.close'), /Timed out.*Browser.close/);
  assert.equal(client.pending.size, 0);
  socket.reply({ id: socket.sent[0].id, result: {} });
  assert.equal(client.pending.size, 0);
});

test('socket errors reject outstanding requests', async () => {
  const { socket, client } = connection();
  const failure = failureWithinDeadline(client.send('Page.enable'), /connection error/);
  socket.onerror?.();
  await failure;
  assert.equal(client.pending.size, 0);
});

test('a synchronous send failure releases its request', async () => {
  const { socket, client } = connection();
  socket.send = () => { throw new Error('Socket is not open'); };
  await assert.rejects(client.send('Page.enable'), /Socket is not open/);
  assert.equal(client.pending.size, 0);
});

test('protocol events do not settle unrelated pending requests', async () => {
  const { socket, client } = connection();
  const events = [];
  client.on('Page.loadEventFired', event => events.push(event));
  const request = client.send('Page.enable');
  socket.reply({ method: 'Page.loadEventFired', params: { timestamp: 7 } });
  assert.deepEqual(events, [{ timestamp: 7 }]);
  assert.equal(client.pending.size, 1);
  socket.reply({ id: socket.sent[0].id, result: {} });
  await request;
});
