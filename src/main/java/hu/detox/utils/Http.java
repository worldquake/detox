package hu.detox.utils;

import lombok.Getter;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

@Getter
public class Http {
    private static final HttpComponentsClientHttpRequestFactory REQ_FACTORY;
    private static final BasicCookieStore COOKIE_STORE;

    static {
        COOKIE_STORE = new BasicCookieStore();
        HttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(COOKIE_STORE)
                .setRedirectStrategy(new DefaultRedirectStrategy())
                .build();
        REQ_FACTORY = new HttpComponentsClientHttpRequestFactory(httpClient);
        //REQ_FACTORY.setConnectionRequestTimeout(30);
        //REQ_FACTORY.setReadTimeout(30);
    }

    public static HttpClient cli() {
        return REQ_FACTORY.getHttpClient();
    }

    private final RestClient client;

    public Http(String baseUrl) {
        Builder builder = RestClient.builder()
                .requestFactory(REQ_FACTORY)
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .defaultHeader("Accept-Language", "hu,en;q=0.9")
                .defaultHeader("Connection", "keep-alive");

        this.client = builder.build();
    }

    public ResponseEntity<String> get(String uri) {
        return client.get().uri(uri)
                .retrieve().toEntity(String.class);
    }

    public ResponseEntity<String> post(String uri, Object data) {
        return client.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(data == null ? "" : data)
                .retrieve().toEntity(String.class);
    }
}