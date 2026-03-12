package com.example.order.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;
    private final String from;

    public EmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from}") String from) {
        this.resend = new Resend(apiKey);
        this.from = from;
    }

    public void send(String to, String subject, String htmlBody, String idempotencyKey) throws ResendException {
        CreateEmailOptions request = CreateEmailOptions.builder()
                .from(from)
                .to(List.of(to))
                .subject(subject)
                .html(htmlBody)
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        resend.emails().send(request, options);
        log.debug("Email sent to {} (idempotencyKey={})", to, idempotencyKey);
    }
}
