const WebSocket = require("ws");

const token = process.argv[2];
const ws = new WebSocket("ws://localhost:8080/ws");

ws.on("open", () => {
  console.log("socket open");
  ws.send(
    "CONNECT\nAuthorization:Bearer " +
      token +
      "\naccept-version:1.2,1.1,1.0\nheart-beat:10000,10000\n\n\0"
  );
  setTimeout(() => {
    console.log("subscribing after 1s...");
    ws.send(
      "SUBSCRIBE\nid:sub-0\ndestination:/topic/org.fd721925-b2fb-4cb8-a052-71d055231395.incidents\n\n\0"
    );
  }, 1000);
});

ws.on("message", (data) => {
  console.log("FRAME:\n" + data.toString().substring(0, 200));
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
  console.log("ALIVE AFTER 6s - no error");
  process.exit(0);
}, 6000);
