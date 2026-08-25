package org.example.all_my_trip_project.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 재설정 링크를 보낸다.
 *
 * <p>메일 설정이 없으면 링크를 <b>서버 로그에만</b> 남긴다. 화면으로는 절대 돌려주지
 * 않는다 — 응답에 실어 보내면 남의 이메일만 알아도 비밀번호를 바꿀 수 있다. 로그는
 * 서버에 들어갈 수 있는 사람만 보므로, 키를 넣기 전에도 개발자가 흐름을 끝까지
 * 확인할 수 있다.
 *
 * <p>이 저장소가 외부 키를 다루는 방식과 같다. LiteAPI 키가 없으면 요금 보강을 건너뛰고
 * 로그를 남기듯, 여기서도 없으면 없는 대로 이어간다.
 */
@Slf4j
@Component
public class PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final String fromAddress;

    public PasswordResetMailer(
            ObjectProvider<JavaMailSender> mailSenders,
            @Value("${spring.mail.username:}") String fromAddress
    ) {
        this.mailSenders = mailSenders;
        this.fromAddress = fromAddress;
    }

    public void send(String email, String resetUrl) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null || fromAddress == null || fromAddress.isBlank()) {
            log.warn("""
                    메일 설정이 없어 비밀번호 재설정 메일을 보내지 못했습니다. \
                    아래 링크로 직접 확인할 수 있습니다. \
                    보내려면 SPRING_MAIL_HOST·SPRING_MAIL_USERNAME·SPRING_MAIL_PASSWORD를 설정하세요.
                      받는 사람: {}
                      링크: {}""", email, resetUrl);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[All My Trips] 비밀번호 재설정 안내");
        message.setText("""
                안녕하세요. All My Trips입니다.

                아래 링크에서 새 비밀번호를 설정해 주세요.

                %s

                이 링크는 30분 뒤에 만료되고, 한 번만 사용할 수 있습니다.
                본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다. 비밀번호는 바뀌지 않습니다.
                """.formatted(resetUrl));

        try {
            sender.send(message);
            log.info("비밀번호 재설정 메일을 보냈습니다. 받는 사람={}", email);
        } catch (RuntimeException exception) {
            /*
             * 발송 실패를 손님에게 그대로 올리지 않는다. 화면에는 "보냈다"로 같은 답을
             * 주고 있어서(계정 존재 여부를 감추려고), 여기서 예외를 던지면 그 사정이
             * 응답 차이로 드러난다.
             */
            log.warn("비밀번호 재설정 메일 발송에 실패했습니다. 받는 사람={}", email, exception);
        }
    }
}
