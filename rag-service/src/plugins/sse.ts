import type { FastifyReply } from "fastify";

export function startSse(reply: FastifyReply) {
  reply.raw.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });
}

export function sendSseChunk(reply: FastifyReply, text: string) {
  reply.raw.write(text);
}

export function endSse(reply: FastifyReply) {
  reply.raw.end();
}

export function sendSseError(reply: FastifyReply, message: string) {
  reply.raw.write(`[ERROR] ${message}`);
  reply.raw.end();
}
