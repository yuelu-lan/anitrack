import { describe, it, expect, vi } from "vitest";

describe("chain", () => {
  it("streamAnswer yields tokens", async () => {
    const { streamAnswer } = await import("./chain.js");
    const fakeRetriever = vi.fn().mockResolvedValue([
      { pageContent: "测试番剧简介", metadata: { animeId: "1" } },
    ]);
    const tokens: string[] = [];
    for await (const t of streamAnswer("问题", fakeRetriever, 4, async function* () {
      yield "测";
      yield "试";
    })) {
      tokens.push(t);
    }
    expect(tokens).toEqual(["测", "试"]);
  });
});
