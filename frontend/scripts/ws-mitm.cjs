const { WebSocketServer, WebSocket } = require("ws");
const { Client } = require("@stomp/stompjs");

const wss = new WebSocketServer({ port: 8099 });
wss.on("connection", (clientSocket) => {
  console.log("[proxy] client connected");
  const upstream = new WebSocket("ws://localhost:8080/ws");

  upstream.on("open", () => console.log("[proxy] upstream open"));
  upstream.on("message", (data) => {
    console.log("[S->C]", JSON.stringify(data.toString()));
    if (clientSocket.readyState === 1) clientSocket.send(data.toString());
  });
  upstream.on("error", (err) => console.log("[proxy] upstream error:", err.message));
  upstream.on("close", (code, reason) => {
    console.log("[proxy] upstream closed:", code, reason.toString());
    clientSocket.close();
  });

  clientSocket.on("message", (data) => {
    console.log("[C->S]", JSON.stringify(data.toString()));
    if (upstream.readyState === 1) upstream.send(data.toString());
  });
  clientSocket.on("close", () => upstream.close());
});

wss.on("listening", () => {
  const client = new Client({
    brokerURL: "ws://localhost:8099/ws",
    webSocketFactory: () => new WebSocket("ws://localhost:8099/ws"),
    connectHeaders: { Authorization: "Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzaWduYWxvcHMiLCJzdWIiOiI4ZThhYWUxZi1hMDRmLTQ3MzQtOTE0ZS1lMjQ4MTA2YjJjMDYiLCJqdGkiOiI1MzdmMDdlNC00OTVmLTRjMWItYmU4NS1kNjc1NGNjMTBlOWIiLCJpYXQiOjE3ODc2NzQ5NzMsImV4cCI6MTc4NzY3NTg3M30.h-Chcu5Q9jqIXleRn6TPi1HmLQ9RjaaenbjSDPXsGcw" },
    reconnectDelay: 0,
  });
  client.onConnect = () => {
    console.log("[stompjs] CONNECTED OK");
    setTimeout(() => process.exit(0), 500);
  };
  client.onStompError = (frame) => {
    console.log("[stompjs] ERROR frame:", frame.headers.message);
    setTimeout(() => process.exit(1), 200);
  };
  client.activate();
});
