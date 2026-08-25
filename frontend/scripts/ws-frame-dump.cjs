const { WebSocketServer } = require("ws");
const WebSocket = require("ws");
const { Client } = require("@stomp/stompjs");

const wss = new WebSocketServer({ port: 8099 });
wss.on("connection", (socket) => {
  console.log("ws server: connection established");
  socket.on("message", (data) => {
    console.log("=== STOMP FRAME FROM STOMPJS ===");
    console.log(JSON.stringify(data.toString()));
    socket.send("CONNECTED\nversion:1.2\nuser-name:test\n\n\0");
  });
  socket.on("error", (err) => console.log("socket error: " + err.message));
});

wss.on("listening", () => {
  const client = new Client({
    brokerURL: "ws://localhost:8099/ws",
    webSocketFactory: () => new WebSocket("ws://localhost:8099/ws"),
    connectHeaders: { Authorization: "Bearer test-token" },
    reconnectDelay: 0,
  });
  client.onConnect = () => {
    console.log("stompjs: connected to fake broker OK");
    process.exit(0);
  };
  client.onStompError = (frame) => {
    console.log("stompjs got error: " + frame.headers.message);
    process.exit(1);
  };
  client.activate();
});
