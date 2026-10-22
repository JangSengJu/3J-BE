package com.jjj.jjj.domain.ai.client;

import java.util.function.Consumer;

public interface GeminiClient {

    boolean isAvailable();

    String model();

    String generateJson(String systemInstruction, String userPrompt) throws Exception;

    void streamMarkdown(String systemInstruction, String userPrompt, Consumer<String> onDelta) throws Exception;
}
