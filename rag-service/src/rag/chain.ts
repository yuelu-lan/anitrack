import { ChatPromptTemplate } from "@langchain/core/prompts";
import { StringOutputParser } from "@langchain/core/output_parsers";
import { RunnablePassthrough, RunnableSequence } from "@langchain/core/runnables";
import type { Document } from "@langchain/core/documents";
import type { BaseChatModel } from "@langchain/core/language_models/chat_models";

const prompt = ChatPromptTemplate.fromTemplate(
  `你是一个番剧百科助手。根据下方参考资料回答问题；若资料不足请说明。

参考资料：
{context}

问题：{question}`
);

const formatDocs = (docs: Document[]) =>
  docs.map((d) => d.pageContent).join("\n\n");

export async function* streamAnswer(
  question: string,
  retriever: (q: string, k: number) => Promise<Document[]>,
  topK: number,
  model: BaseChatModel | (() => AsyncIterable<string>)
): AsyncGenerator<string> {
  const docs = await retriever(question, topK);
  const chain = RunnableSequence.from([
    { context: () => formatDocs(docs), question: new RunnablePassthrough() },
    prompt,
    model as BaseChatModel,
    new StringOutputParser(),
  ]);
  const stream = await chain.stream(question);
  for await (const chunk of stream) {
    yield chunk as string;
  }
}
