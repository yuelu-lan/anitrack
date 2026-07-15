import type { FastifyInstance } from "fastify";
import { startSse, sendSseChunk, endSse, sendSseError } from "../plugins/sse.js";

export default async function queryRoute(app: FastifyInstance) {
  app.post("/", async (req, reply) => {
    const { question, topK } = req.body as { question: string; topK?: number };
    if (!question) {
      return reply.code(400).send({ error: "question required" });
    }
    startSse(reply);
    try {
      const streamAnswerFn = (app as any).streamAnswer;
      const retrieveFn = (app as any).retrieve;
      const model = (app as any).createModel();
      for await (const token of streamAnswerFn(question, retrieveFn, topK ?? 4, model)) {
        sendSseChunk(reply, token);
      }
      endSse(reply);
    } catch (e: any) {
      app.log.error(e);
      sendSseError(reply, e?.message ?? "unknown");
    }
  });
}
