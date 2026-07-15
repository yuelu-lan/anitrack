import { describe, it, expect, vi } from "vitest";
import Fastify from "fastify";

describe("query route", () => {
  it("streams tokens as text", async () => {
    const app = Fastify();
    const fakeStream = async function* () { yield "你好"; };
    app.decorate("streamAnswer", vi.fn().mockReturnValue(fakeStream()));
    app.decorate("retrieve", vi.fn());
    app.decorate("createModel", vi.fn());
    const { default: queryRoute } = await import("./query.js");
    await app.register(queryRoute, { prefix: "/query" });

    const res = await app.inject({
      method: "POST",
      url: "/query",
      headers: { "x-internal-token": "t" },
      payload: { question: "你好" },
    });
    expect(res.statusCode).toBe(200);
    expect(res.body).toContain("你好");
  });
});
