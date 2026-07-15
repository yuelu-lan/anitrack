import Fastify from "fastify";
import { config } from "./config.js";

const app = Fastify({ logger: true });

app.get("/health", async () => ({ status: "ok" }));

const start = async () => {
  try {
    await app.listen({ port: config.port, host: "0.0.0.0" });
  } catch (e) {
    app.log.error(e);
    process.exit(1);
  }
};
start();
