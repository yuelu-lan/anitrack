import { describe, it, expect, vi } from "vitest";
import Fastify from "fastify";

describe("documents route", () => {
  it("lists documents via decorated listDocuments", async () => {
    const app = Fastify();
    app.decorate("listDocuments", vi.fn().mockResolvedValue([
      { animeId: "1", title: "标题A" },
      { animeId: "2", title: "标题B" },
    ]));
    const { default: documentsRoute } = await import("./documents.js");
    await app.register(documentsRoute, { prefix: "/documents" });

    const res = await app.inject({
      method: "GET",
      url: "/documents",
      headers: { "x-internal-token": "t" },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({
      documents: [
        { animeId: "1", title: "标题A" },
        { animeId: "2", title: "标题B" },
      ],
    });
  });
});
