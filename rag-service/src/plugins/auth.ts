import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import { config } from "../config.js";

export async function authPlugin(app: FastifyInstance) {
  app.addHook("onRequest", async (req: FastifyRequest, reply: FastifyReply) => {
    if (req.url === "/health") return;
    const token = req.headers["x-internal-token"];
    if (token !== config.internalToken) {
      reply.code(401).send({ error: "unauthorized" });
    }
  });
}
