package org.springframework.ai.mcp.samples.brave;

import java.util.List;
import java.util.Scanner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public CommandLineRunner chatbot(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools) {

		return args -> {

			var chatClient = chatClientBuilder
					.defaultSystem("You are useful assistant and can perform web searches Brave's search API to reply to your questions.")
					.defaultTools(tools)
					.defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
					.build();

			// Start the chat loop
			System.out.println("\nI am your AI assistant.\n");
			try (Scanner scanner = new Scanner(System.in)) {
				while (true) {
					System.out.print("\nUSER: ");
					String str = scanner.nextLine();
					ChatClient.CallResponseSpec call = chatClient.prompt(str).call();
					ChatResponse chatResponse = call.chatResponse();
					Generation result = chatResponse.getResult();
					AssistantMessage output = result.getOutput();
					String text = output.getText();
					System.out.println("\nASSISTANT: " + text);
				}
			}

		};
	}
}