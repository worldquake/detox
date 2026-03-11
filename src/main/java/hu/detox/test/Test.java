package hu.detox.test;

import hu.Main;
import hu.detox.utils.strings.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@SpringBootApplication
@RequiredArgsConstructor
@Import(Main.class)
public class Test implements ApplicationListener<ContextRefreshedEvent> {
    public static final String SYSTEM_PROMPT = """
            You are an AI assistant I test. I want you to just go with the flow, and notice the tools I configured, call them when appropriate.
            """;

    private ChatClient.ChatClientRequestSpec prompt;

    @Tool(description = """
            When the user asks for information (anything) then call this, if returns null then answer yourself as much as you can in a funny way.
            So if he says What is the time, or wants to know bitcoin price, just call this.""")
    public String getInfo(String topic) {
        if (topic.contains("weather")) {
            return "The weather is sunny.";
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Main.main(Test.class, args);
    }

    @SneakyThrows
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ChatClient.Builder builder = Main.ctx().getBean(ChatClient.Builder.class);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
        ChatClient chat = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        prompt = chat.prompt().system(SYSTEM_PROMPT).tools(this);
        sendMessage("Tell me a joke!");
        sendMessage("What's the weather?");
        var br = new BufferedReader(new InputStreamReader(System.in));
        String ln;
        while (StringUtils.isNotBlank(ln = br.readLine())) {
            System.err.println("Ask: ");
            sendMessage(ln);
        }
    }

    private ChatClient.CallResponseSpec sendMessage(String msg) {
        System.out.println("User: " + msg);
        var res = prompt.user(msg).call();
        System.out.println("AI: " + res.content());
        return res;
    }
}
