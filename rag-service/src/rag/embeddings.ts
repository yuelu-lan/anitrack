import { OpenAIEmbeddings } from "@langchain/openai";
import { config } from "../config.js";

export const embeddings = new OpenAIEmbeddings({
  apiKey: config.embedding.apiKey,
  model: config.embedding.model,
  configuration: { baseURL: config.embedding.baseURL },
});
