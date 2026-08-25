const WebSocket = require("ws");

const token = process.argv[2];
const ws = new WebSocket("ws://localhost:8080/ws");

ws.on("open", () => {
  console.log("socket open");
  ws.send(
    "CONNECT\naccept-version:1.2,1.1,1.0\nheart-beat:10000,10000\nAuthorization:Bearer " +
      token +
      "\n\n\0"
  );
});

ws.on("message", (data) => {
  console.log("FRAME:\n" + data.toString());
  process.exit(0);
});

ws.on("error", (err) => {
  console.log("error: " + err.message);
  process.exit(1);
});

ws.on("close", (code, reason) => {
  console.log("closed: " + code + " " + reason.toString());
  process.exit(1);
});

setTimeout(() => {
  console.log("timeout");
  process.exit(1);
}, 8000);
