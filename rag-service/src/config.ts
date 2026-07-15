import "dotenv/config";

function required(key: string): string {
  const v = process.env[key];
  if (!v) throw new Error(`Missing env ${key}`);
  return v;
}

export const config = {
  port: Number(process.env.PORT ?? 8081),
  internalToken: required("INTERNAL_TOKEN"),
  chromaUrl: required("CHROMA_URL"),
  collectionName: process.env.COLLECTION_NAME ?? "anime_wiki",
  llm: {
    baseURL: required("LLM_BASE_URL"),
    apiKey: required("LLM_API_KEY"),
    model: required("LLM_MODEL"),
  },
  embedding: {
    baseURL: required("EMBEDDING_BASE_URL"),
    apiKey: required("EMBEDDING_API_KEY"),
    model: required("EMBEDDING_MODEL"),
  },
  topK: Number(process.env.TOP_K ?? 4),
};
