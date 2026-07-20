import type { FastifyInstance } from "fastify";
import { ingestDocuments } from "../rag/vectorStore.js";

export default async function ingestRoute(app: FastifyInstance) {
  app.post("/", async (req, reply) => {
    const { documents } = req.body as {
      documents: {
        pageContent: string;
        metadata: {
          animeId: string;
          title: string;
          originalName?: string;
          airDate?: string;
          score?: number;
          ratingTotal?: number;
        };
      }[];
    };
    if (!documents?.length) {
      return reply.code(400).send({ error: "documents required" });
    }
    const n = await ingestDocuments(documents);
    return reply.send({ ingested: n });
  });
}
