import Fastify from "fastify";
import { ChatOpenAI } from "@langchain/openai";
import { config } from "./config.js";
import { authPlugin } from "./plugins/auth.js";
import ingestRoute from "./routes/ingest.js";
import queryRoute from "./routes/query.js";
import { streamAnswer } from "./rag/chain.js";
import { retrieve } from "./rag/retriever.js";

const app = Fastify({ logger: true });

app.get("/health", async () => ({ status: "ok" }));

app.decorate("streamAnswer", streamAnswer);
app.decorate("retrieve", retrieve);
app.decorate("createModel", () =>
  new ChatOpenAI({
    apiKey: config.llm.apiKey,
    model: config.llm.model,
    configuration: { baseURL: config.llm.baseURL },
    streaming: true,
  }),
);

await app.register(authPlugin);
await app.register(ingestRoute, { prefix: "/ingest" });
await app.register(queryRoute, { prefix: "/query" });

const start = async () => {
  try {
    await app.listen({ port: config.port, host: "0.0.0.0" });
  } catch (e) {
    app.log.error(e);
    process.exit(1);
  }
};
start();
