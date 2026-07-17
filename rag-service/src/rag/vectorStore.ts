import { Chroma } from "@langchain/community/vectorstores/chroma";
import { IncludeEnum } from "chromadb";
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

export async function listDocuments(): Promise<{ animeId: string; title: string }[]> {
  const store = await getVectorStore();
  const col = store.collection;
  if (!col) throw new Error("chroma collection not initialized");
  const res = await col.get({ include: [IncludeEnum.Metadatas], limit: 1000 });
  const ids = res.ids ?? [];
  const metadatas = res.metadatas ?? [];
  return ids.map((id, i) => ({
    animeId: id,
    title: (metadatas[i] as { title?: string } | null)?.title ?? "",
  }));
}
