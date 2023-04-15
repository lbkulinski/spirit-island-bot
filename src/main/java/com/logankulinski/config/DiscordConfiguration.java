package com.logankulinski.config;

import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.Objects;
import reactor.core.publisher.Mono;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import reactor.core.publisher.Flux;
import discord4j.core.event.domain.message.MessageCreateEvent;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import org.springframework.context.annotation.Bean;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.DiscordClientBuilder;
import org.springframework.beans.factory.BeanInitializationException;

@Configuration
public class DiscordConfiguration {
    private final String token;

    private static final Logger LOGGER;

    static {
        LOGGER = LoggerFactory.getLogger(DiscordConfiguration.class);
    }

    @Autowired
    public DiscordConfiguration(@Value("${discord.token}") String token) {
        Objects.requireNonNull(token);

        this.token = token;
    }

    private Mono<Message> handleMatch(String match, Mono<MessageChannel> channel) {
        Objects.requireNonNull(match);

        Objects.requireNonNull(channel);

        DiscordConfiguration.LOGGER.info("Match: {}", match);

        String transformedMatch = match.toLowerCase()
                                       .replaceAll("\\s+", "_")
                                       .replaceAll("\\W", "");

        DiscordConfiguration.LOGGER.info("Transformed match: {}", transformedMatch);

        String uriString = "https://sick.oberien.de/imgs/powers/%s.webp".formatted(transformedMatch);

        URI uri = URI.create(uriString);

        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .GET()
                                         .build();

        HttpResponse.BodyHandler<Void> bodyHandler = HttpResponse.BodyHandlers.discarding();

        CompletableFuture<Integer> future = HttpClient.newHttpClient()
                                                      .sendAsync(request, bodyHandler)
                                                      .thenApply(HttpResponse::statusCode);

        return Mono.fromFuture(future)
                   .flatMap(statusCode -> {
                       int ok = 200;

                       String messageContent;

                       if (statusCode == ok) {
                           messageContent = uriString;
                       } else {
                           messageContent = "Sorry, a power card named **%s** could not be found.".formatted(match);
                       }

                       return channel.flatMap(messageChannel -> messageChannel.createMessage(messageContent));
                   });
    }

    private Flux<Message> handleEvent(MessageCreateEvent event) {
        Objects.requireNonNull(event);

        Message message = event.getMessage();

        String content = message.getContent();

        Pattern pattern = Pattern.compile("\\[\\[([^]]+)]]");

        Matcher matcher = pattern.matcher(content);

        Mono<MessageChannel> channel = message.getChannel();

        List<Mono<Message>> monos = matcher.results()
                                           .map(result -> result.group(1))
                                           .map(match -> this.handleMatch(match, channel))
                                           .toList();

        return Flux.concat(monos);
    }

    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
        GatewayDiscordClient client = DiscordClientBuilder.create(this.token)
                                                          .build()
                                                          .login()
                                                          .block();

        if (client == null) {
            String message = "The Discord client is null";

            throw new BeanInitializationException(message);
        }

        client.on(MessageCreateEvent.class, this::handleEvent)
              .subscribe();

        return client;
    }
}
