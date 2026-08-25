/* Phase 2 realtime E2E: connect to /ws with a real token, subscribe to the org topic,
   trigger a REST mutation, and assert the after-commit broadcast arrives. */
const { Client, version } = require("@stomp/stompjs");
const WebSocket = require("ws");

const API = "http://localhost:8080/api/v1";

async function main() {
  // 1. login
  const loginResponse = await fetch(API + "/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: "owner@demo.signalops.io", password: "Demo1234!" }),
  });
  if (!loginResponse.ok) throw new Error("login failed: " + loginResponse.status);
  const login = await loginResponse.json();
  const meResponse = await fetch(API + "/auth/me", {
    headers: { Authorization: "Bearer " + login.accessToken },
  });
  const me = await meResponse.json();
  const orgId = me.memberships[0].organizationId;
  console.log("logged in, org:", orgId);

  // 2. create an incident to mutate later
  const servicesResponse = await fetch(API + "/services", {
    headers: { Authorization: "Bearer " + login.accessToken, "X-Organization-Id": orgId },
  });
  const services = await servicesResponse.json();
  const createResponse = await fetch(API + "/incidents", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer " + login.accessToken,
      "X-Organization-Id": orgId,
    },
    body: JSON.stringify({
      title: "WS broadcast verification",
      description: "",
      severity: "LOW",
      serviceIds: [services[0].id],
      commanderUserId: null,
      responderUserIds: [],
    }),
  });
  const incident = await createResponse.json();
  console.log("created", incident.key, "v" + incident.version);

  // 3. connect STOMP and subscribe
  let received = null;
  const client = new Client({
    brokerURL: "ws://localhost:8080/ws",
    webSocketFactory: () => new WebSocket("ws://localhost:8080/ws"),
    connectHeaders: { Authorization: "Bearer " + login.accessToken },
    reconnectDelay: 0,
    debug: (str) => console.log("STOMP-DEBUG:" + str),
  });
  client.onWebSocketClose = (event) => { console.log('ws close:', JSON.stringify(event)); };
  client.onStompError = (frame) => { console.log('stomp error:', frame.headers['message'], frame.body); };
  client.onWebSocketError = (event) => { console.log('ws error:', event.message || event.type); };
  client.onConnect = () => {
    client.subscribe("/topic/org." + orgId + ".incidents", (message) => {
      received = JSON.parse(message.body);
    });
  };
  client.activate();

  await new Promise((resolve, reject) => {
    const started = Date.now();
    const timer = setInterval(() => {
      if (client.connected) {
        clearInterval(timer);
        resolve(null);
      } else if (Date.now() - started > 15000) {
        clearInterval(timer);
        reject(new Error("STOMP connect timeout"));
      }
    }, 200);
  });
  await new Promise((resolve) => setTimeout(resolve, 500));
  console.log("STOMP connected");

  // 4. mutate via REST -> after-commit broadcast
  const transitionResponse = await fetch(API + "/incidents/" + incident.id + "/transitions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer " + login.accessToken,
      "X-Organization-Id": orgId,
    },
    body: JSON.stringify({ to: "INVESTIGATING", version: incident.version }),
  });
  if (!transitionResponse.ok) throw new Error("transition failed: " + transitionResponse.status);

  await new Promise((resolve, reject) => {
    const started = Date.now();
    const timer = setInterval(() => {
      if (received) {
        clearInterval(timer);
        resolve(null);
      } else if (Date.now() - started > 10000) {
        clearInterval(timer);
        reject(new Error("no WS message received within 10s"));
      }
    }, 200);
  });

  console.log("WS MESSAGE RECEIVED:", JSON.stringify(received));
  if (received.type !== "STATUS_CHANGED" || received.data.to !== "INVESTIGATING") {
    throw new Error("unexpected payload");
  }
  console.log("REALTIME E2E PASS");
  client.deactivate();
}

main().catch((error) => {
  console.error("REALTIME E2E FAIL:", error.message);
  process.exit(1);
});
