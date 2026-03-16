package hu.detox.test;

import hu.Main;
import hu.detox.spring.ConditionalOnNoApp;
import hu.detox.spring.DetoxConfig;
import hu.detox.utils.strings.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@SpringBootApplication
@RequiredArgsConstructor
@ConditionalOnNoApp.Annotation
@Import(DetoxConfig.class)
public class Test implements CommandLineRunner {
    public static final String SYSTEM_PROMPT = """
            You are an AI assistant I test. I want you to just go with the flow, and notice the tools I configured, call them when appropriate.
            """;

    private ChatClient.ChatClientRequestSpec prompt;

    @Tool(description = """
            When the user asks for information (anything) then call this, if returns null then answer yourself as much as you can in a funny way.
            So if he says What is the time, or wants to know bitcoin price, just call this. But do not call if the user just asks you to say things, this can be called specificaly only if you cannot know.""")
    public String getInfo(String topic) {
        System.err.println("CALLED: " + topic);
        if (topic.contains("weather")) {
            return "The weather is sunny.";
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Main.main(Test.class, args);
    }

    private ChatClient.CallResponseSpec sendMessage(String msg) {
        System.out.println("User: " + msg);
        var res = prompt.user(msg).call();
        System.out.println("AI: " + res.content());
        return res;
    }

    @Override
    public void run(String... args) throws Exception {
        ApplicationContext ctx = Main.ctx();
        ChatClient.Builder builder = ctx.getBean(ChatClient.Builder.class);
        ChatMemory chatMemory = ctx.getBean(ChatMemory.class);
        ChatClient chat = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();

        prompt = chat.prompt().system(SYSTEM_PROMPT).tools(this);
        var br = new BufferedReader(new InputStreamReader(System.in));
        String ln;
        System.err.print("Ask questions or just enter to quit: ");
        while (StringUtils.isNotBlank(ln = br.readLine())) {
            sendMessage(ln);
            System.out.print("Ask: ");
            System.out.flush();
        }
    }
}
