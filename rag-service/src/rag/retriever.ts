import type { Document } from "@langchain/core/documents";
import { getVectorStore } from "./vectorStore.js";
import { config } from "../config.js";

export async function retrieve(question: string, topK: number = config.topK): Promise<Document[]> {
  const store = await getVectorStore();
  return store.similaritySearch(question, topK);
}
