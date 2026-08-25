const WebSocket = require("ws");

const ws = new WebSocket("ws://localhost:8080/ws");

ws.on("open", () => {
  console.log("socket open");
  ws.send("CONNECT\naccept-version:1.2\nAuthorization:Bearer eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzaWduYWxvcHMiLCJzdWIiOiI4ZThhYWUxZi1hMDRmLTQ3MzQtOTE0ZS1lMjQ4MTA2YjJjMDYiLCJqdGkiOiIwMzlmZWE0OC1mMWY1LTQ2MDctODYyMC00NGI5OWRjODMwY2EiLCJpYXQiOjE3ODc2Njk1MjMsImV4cCI6MTc4NzY3MDQyM30.md-l__z-YwKcYOHS7XozXC_MaaPVg3Hdt4R0IxSBdhc\n\n\0");
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
