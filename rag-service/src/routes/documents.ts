import type { FastifyInstance } from "fastify";

export default async function documentsRoute(app: FastifyInstance) {
  app.get("/", async (_req, reply) => {
    try {
      const documents = await (app as any).listDocuments();
      return reply.send({ documents });
    } catch (e: any) {
      app.log.error(e);
      return reply.code(500).send({ error: e?.message ?? "list documents failed" });
    }
  });
}
