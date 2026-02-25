package hu.detox;

import hu.detox.spring.DetoxConfig;
import hu.detox.utils.reflection.ReflectionUtils;
import hu.detox.utils.strings.StringUtils;
import org.jline.utils.AttributedString;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.shell.command.CommandRegistration;

import java.util.concurrent.atomic.AtomicReference;

@SpringBootApplication(
        scanBasePackageClasses = DetoxConfig.class
)
@Primary
public class Main implements BeanPostProcessor {
    public static AttributedString PROMPT = new AttributedString("DeToX> ");

    public static CommandRegistration.Builder cr(String cmd) {
        AtomicReference<String> rCmd = new AtomicReference<>(cmd);
        Class<?> ref = ReflectionUtils.getCaller(null, els -> {
            StackTraceElement ret = null;
            boolean isCr;
            for (StackTraceElement el : els) {
                isCr = el.getMethodName().equals("cr");
                if (isCr) ret = el;
                else if (ret != null) {
                    boolean nu = cmd != null && cmd.startsWith(StringUtils.NULL_UNI);
                    if (cmd == null || nu) {
                        if (nu) {
                            if (cmd.length() == 1) rCmd.set(el.getMethodName());
                            else rCmd.set(cmd.substring(1));
                        }
                        ret = el;
                    }
                    break;
                }
            }
            return ret;
        }).getOn();
        String fcmd = rCmd.get();
        fcmd = hu.detox.Main.toCommand(ref) + (StringUtils.isBlank(fcmd) ? "" : " " + fcmd);
        return CommandRegistration.builder().group("DeToX").command(fcmd.trim());
    }

    public static String toCommand(Class<?> caller) {
        String pkg = caller.getPackage().getName()
                .replace(".spring", "");
        return toCommand(pkg);
    }

    private static String toCommand(String pkg) {
        String[] pkga = StringUtils.split(pkg, '.');
        String[] ra = StringUtils.split(System.getProperty("root"), '.');
        int from = 0;
        for (int i = 0; i < pkga.length && i < ra.length; i++) {
            if (pkga[i].equals(ra[i])) from = i;
            else break;
        }
        pkg = StringUtils.join(pkga, '.', from + 1, pkga.length);
        return pkg.replaceFirst("^\\.", "")
                .replaceAll("\\.", " ");
    }

    public static void main(String[] args) throws Exception {
        main(Main.class, args);
    }

    public static ApplicationContext main(Class<?> any, String[] args) throws Exception {
        try {
            PROMPT = ReflectionUtils.getProperty(any, "PROMPT");
        } catch (IllegalArgumentException ia) {
            // No worries
        }
        return hu.Main.main(any, args);
    }
}
