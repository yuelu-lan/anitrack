import { Chroma } from "@langchain/community/vectorstores/chroma";
import { embeddings } from "./embeddings.js";
import { config } from "../config.js";

export async function getVectorStore(): Promise<Chroma> {
  return Chroma.fromExistingCollection(embeddings, {
    url: config.chromaUrl,
    collectionName: config.collectionName,
  });
}

export async function ingestDocuments(
  docs: { pageContent: string; metadata: Record<string, string> }[]
): Promise<number> {
  const store = await getVectorStore();
  await store.addDocuments(docs, { ids: docs.map((d) => d.metadata.animeId) });
  return docs.length;
}
