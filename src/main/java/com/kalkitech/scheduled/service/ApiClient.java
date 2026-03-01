package com.kalkitech.scheduled.service;

import com.kalkitech.scheduled.config.AppProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

@Component
public class ApiClient {

    public record ApiResult(int statusCode, String contentType, String body) {}

    private final WebClient webClient;
    private final AppProperties props;

    public ApiClient(AppProperties props) {
        this.props = props;

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();

        ConnectionProvider provider = ConnectionProvider.builder("hes-api")
                .maxConnections(props.getApi().getMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(props.getApi().getPendingAcquireTimeoutMs()))
                .maxIdleTime(Duration.ofMillis(props.getApi().getMaxIdleTimeMs()))
                .maxLifeTime(Duration.ofMillis(props.getApi().getMaxLifeTimeMs()))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getApi().getConnectTimeoutMs())
                // TCP read timeout at Netty level
                .responseTimeout(Duration.ofMillis(props.getApi().getReadTimeoutMs()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(props.getApi().getReadTimeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(props.getApi().getReadTimeoutMs(), TimeUnit.MILLISECONDS))
                );

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    public Mono<ApiResult> postRaw(Object dtoJson) {
        return webClient.post()
                .uri(props.getApi().getUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.ALL)
                .header("Authorization", "Bearer " + props.getApi().getBearerToken())
                .bodyValue(dtoJson)
                .exchangeToMono(this::toResult)
                // transient network failures happen under burst load; retry a couple of times
                .retryWhen(Retry.backoff(props.getApi().getRetries(), Duration.ofMillis(props.getApi().getRetryBackoffMs()))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryable)
                );
    }

    private boolean isRetryable(Throwable t) {
        if (t instanceof WebClientRequestException) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains("connection reset")) return true;
            // any low-level I/O issue (timeouts, reset, etc.) is worth retrying
            return true;
        }
        return false;
    }

    private Mono<ApiResult> toResult(ClientResponse resp) {
        String ct = resp.headers().contentType().map(MediaType::toString).orElse("");
        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new ApiResult(resp.statusCode().value(), ct, body));
    }
}
