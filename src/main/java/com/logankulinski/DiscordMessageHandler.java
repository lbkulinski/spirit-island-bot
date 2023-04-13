package com.logankulinski;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DiscordMessageHandler implements ApplicationRunner {
    private final String token;

    @Autowired
    public DiscordMessageHandler(@Value("${discord.token}") String token) {
        Objects.requireNonNull(token);

        this.token = token;
    }

    private Mono<Message> handleEvent(MessageCreateEvent event) {
        Objects.requireNonNull(event);

        Message message = event.getMessage();

        String content = message.getContent();

        Pattern pattern = Pattern.compile(".*(\\[\\[.*]]).*");

        Matcher matcher = pattern.matcher(content);

        if (!matcher.matches()) {
            return Mono.empty();
        }

        content = matcher.group(1);

        System.out.printf("Content: %s%n", content);

        String transformedContent = content.toLowerCase()
                                           .replaceAll("\\s+", "_")
                                           .replaceAll("\\W", "");

        System.out.printf("Transformed Content: %s%n", transformedContent);

        String uriString = "https://sick.oberien.de/imgs/powers/%s.webp".formatted(transformedContent);

        URI uri = URI.create(uriString);

        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .GET()
                                         .build();

        var future = HttpClient.newHttpClient()
                               .sendAsync(request, HttpResponse.BodyHandlers.discarding())
                               .thenApply(HttpResponse::statusCode);

        return Mono.fromFuture(future)
                   .flatMap(statusCode -> {
                       int ok = 200;

                       String messageContent;

                       if (statusCode == ok) {
                           messageContent = uriString;
                       } else {
                           messageContent = "Sorry, that power card could not be found.";
                       }

                       return message.getChannel()
                                     .flatMap(channel -> channel.createMessage(messageContent));
                   });
    }

    @Override
    public void run(ApplicationArguments args) {
        DiscordClient client = DiscordClient.create(token);

        client.withGateway(gateway -> gateway.on(MessageCreateEvent.class, this::handleEvent))
              .block();
    }
}
