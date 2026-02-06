package hu.detox.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hu.detox.io.CharIOHelper;
import org.jsoup.internal.StringUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class Serde implements Closeable, Flushable {
    public static final ObjectMapper OM = new ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public enum Mode {
        TXT, // ID based input, URL will be created
        JSONL, // List of Json objects, one per line
        SER; // Serialized with headers

        public static Mode toMode(String fileName) {
            Mode ret = SER;
            for (Mode m : values()) {
                if (fileName.endsWith("." + m.name().toLowerCase())) {
                    ret = m;
                    break;
                }
            }
            return ret;
        }
    }

    private static final Function<String, Object> TOSTR = responseBody -> responseBody;
    private PrintStream out;
    private BufferedReader reader;
    private Mode inMode;
    private Mode outMode;

    public Serde(File out, CharIOHelper in) throws IOException {
        if (in != null) {
            reader = in.getReader();
            inMode = Mode.toMode(in.getName());
        }
        if (out != null) {
            this.out = new PrintStream(new FileOutputStream(out));
            outMode = Mode.toMode(out.getName());
        }
    }

    public Mode inMode() {
        return inMode;
    }

    public Mode outMode() {
        return outMode;
    }

    public JsonNode serialize(ResponseEntity<?> response, String url, Function<String, ?> trafo) throws IOException {
        if (trafo == null) {
            trafo = TOSTR;
        }
        String strBody = (url == null ? "" : "<!--" + url + "-->\n") + response.getBody();
        JsonNode bodyNode = null;
        if (Mode.TXT.equals(outMode)) {
            out.println(strBody);
        } else {
            var obj = trafo.apply(strBody);
            if (obj == null) return null;
            bodyNode = OM.valueToTree(obj);
            var body = bodyNode.toString();
            if (Mode.JSONL.equals(outMode)) {
                out.println(body);
            } else {
                List<String> heads = new LinkedList<>();
                response.getHeaders().forEach((s, strings) -> {
                    if (CollectionUtils.isEmpty(strings)) return;
                    heads.add(s + ": " + strings.get(0));
                });
                out.println(heads.size() + " " + body.length());
                out.println("HTTP/1.1 " + response.getStatusCode());
                for (String h : heads) {
                    out.println(h);
                }
                out.println(body);
                out.flush();
            }
        }
        return bodyNode;
    }

    public String nextStr() throws IOException {
        String ln = null;
        var resp = next();
        if (inMode().equals(Serde.Mode.TXT)) {
            ln = (String) resp;
        } else if (inMode().equals(Serde.Mode.JSONL)) {
            ln = resp == null ? null : resp.toString();
        } else if (resp != null) {
            var r = (ResponseEntity) resp;
            var body = r.getBody();
            ln = body == null ? null : body.toString();
        }
        return ln;
    }

    public Object next() throws IOException {
        String statusLine = reader.readLine();
        if (StringUtil.isBlank(statusLine)) return null;
        if (inMode.equals(Mode.TXT)) return statusLine;
        if (inMode.equals(Mode.JSONL)) return Serde.OM.readTree(statusLine);
        int[] contentLengths = new int[2]; // Headers count, and body strlen
        String[] hl = statusLine.split(" ");
        contentLengths[0] = Integer.parseInt(hl[0]);
        contentLengths[1] = Integer.parseInt(hl[1]);
        statusLine = reader.readLine();
        if (!statusLine.startsWith("HTTP/1.1 ")) throw new IOException("Invalid status line: " + statusLine);
        String[] statusParts = statusLine.split(" ", 3);
        int code = Integer.parseInt(statusParts[1]);
        String message = statusParts.length > 2 ? statusParts[2] : "";
        HttpHeaders headers = new HttpHeaders();
        String line;
        for (int i = 0; i < contentLengths[0]; i++) {
            line = reader.readLine();
            int idx = line.indexOf(":");
            if (idx == -1) continue;
            String name = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            headers.add(name, value);
            if (contentLengths[1] <= 0 && name.equalsIgnoreCase("Content-Length")) {
                contentLengths[1] = Integer.parseInt(value);
            }
        }
        if (contentLengths[1] < 0) throw new IOException("Missing Content-Length");

        char[] body = new char[contentLengths[1]];
        int read = 0;
        while (read < contentLengths[1]) {
            int r = reader.read(body, read, contentLengths[1] - read);
            if (r == -1) throw new IOException("Unexpected end of stream, msg=" + message);
            read += r;
        }
        reader.readLine();
        return new ResponseEntity<>(body, headers, HttpStatusCode.valueOf(code));
    }

    @Override
    public void close() throws IOException {
        if (out != null) out.close();
        if (reader != null) reader.close();
    }

    @Override
    public void flush() {
        if (out != null) out.flush();
    }
}