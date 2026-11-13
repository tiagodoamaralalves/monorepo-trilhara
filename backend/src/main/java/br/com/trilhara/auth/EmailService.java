package br.com.trilhara.auth;

import br.com.trilhara.shared.Pseudonimo;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String resendApiKey;
    private final String remetente;
    private final String frontendUrlPadrao;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public EmailService(
            @ConfigProperty(name = "trilhara.email.resend-api-key", defaultValue = "") String resendApiKey,
            @ConfigProperty(name = "trilhara.email.from", defaultValue = "Trilhara <onboarding@resend.dev>") String remetente,
            @ConfigProperty(name = "trilhara.email.frontend-url", defaultValue = "http://localhost:5173") String frontendUrlPadrao,
            ObjectMapper objectMapper) {
        this.resendApiKey = resendApiKey != null ? resendApiKey.trim() : "";
        this.remetente = remetente != null ? remetente.trim() : "Trilhara <onboarding@resend.dev>";
        this.frontendUrlPadrao = frontendUrlPadrao != null ? frontendUrlPadrao.trim() : "http://localhost:5173";
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Envia e-mail de confirmação de cadastro de forma assíncrona/não-bloqueante.
     */
    public CompletableFuture<Boolean> enviarEmailConfirmacao(String destinatario, String nome, String token, String baseUrl) {
        String base = normalizarBaseUrl(baseUrl);
        String link = base + "/?pagina=confirmar-email&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        if (resendApiKey.isBlank()) {
            // D-04/R13: nunca logar destinatario nem o link (o link contém o token puro).
            LOG.warnf("[DEV] Resend API Key não configurada. Confirmação de e-mail emitida conta=%s integracao=resend",
                    Pseudonimo.de(destinatario));
            return CompletableFuture.completedFuture(true);
        }

        String primeiroNome = (nome != null && !nome.isBlank()) ? nome.trim().split(" ")[0] : "Estudante";

        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <title>Confirme seu e-mail — Trilhara</title>
                </head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 24px; margin: 0;">
                  <div style="max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; padding: 32px;">
                    <h2 style="color: #0f172a; margin-top: 0; font-size: 20px;">Bem-vindo(a) ao Trilhara!</h2>
                    <p style="font-size: 15px; line-height: 1.6; color: #334155;">
                      Olá, %s! Seu cadastro foi realizado com sucesso.
                    </p>
                    <p style="font-size: 15px; line-height: 1.6; color: #334155;">
                      Para ativar todos os recursos e garantir a segurança do seu progresso de estudos e tarefas, confirme seu e-mail no botão abaixo:
                    </p>
                    <div style="text-align: center; margin: 28px 0;">
                      <a href="%s" style="background-color: #0284c7; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 6px; font-weight: 600; font-size: 15px; display: inline-block;">
                        Confirmar Meu E-mail
                      </a>
                    </div>
                    <p style="font-size: 13px; line-height: 1.5; color: #64748b;">
                      Este link é válido por 24 horas.<br>
                      Se você não realizou este cadastro, pode desconsiderar esta mensagem com total segurança.
                    </p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; word-break: break-all;">
                      Se o botão não funcionar, copie e cole o link no seu navegador:<br>
                      <a href="%s" style="color: #0284c7;">%s</a>
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(primeiroNome, link, link, link);

        return dispararEmail(destinatario, "Confirme seu e-mail — Trilhara", html);
    }

    /**
     * Envia e-mail de recuperação de senha de forma assíncrona/não-bloqueante.
     */
    public CompletableFuture<Boolean> enviarEmailRecuperacaoSenha(String destinatario, String token, String baseUrl) {
        String base = normalizarBaseUrl(baseUrl);
        String link = base + "/?pagina=resetar-senha&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        if (resendApiKey.isBlank()) {
            // D-04/R13: o link de recuperação contém o token puro — não pode ir para o log.
            LOG.warnf("[DEV] Resend API Key não configurada. Recuperação de senha emitida conta=%s integracao=resend",
                    Pseudonimo.de(destinatario));
            return CompletableFuture.completedFuture(true);
        }

        String html = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <title>Recuperação de Senha — Trilhara</title>
                </head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 24px; margin: 0;">
                  <div style="max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; padding: 32px;">
                    <h2 style="color: #0f172a; margin-top: 0; font-size: 20px;">Recuperação de Senha — Trilhara</h2>
                    <p style="font-size: 15px; line-height: 1.6; color: #334155;">
                      Olá! Recebemos uma solicitação para redefinir a senha da sua conta no Trilhara.
                    </p>
                    <p style="font-size: 15px; line-height: 1.6; color: #334155;">
                      Para criar uma nova senha, clique no botão abaixo:
                    </p>
                    <div style="text-align: center; margin: 28px 0;">
                      <a href="%s" style="background-color: #0284c7; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 6px; font-weight: 600; font-size: 15px; display: inline-block;">
                        Redefinir Minha Senha
                      </a>
                    </div>
                    <p style="font-size: 13px; line-height: 1.5; color: #64748b;">
                      Este link é de uso único e expira em 15 minutos.<br>
                      Se você não solicitou a redefinição de senha, nenhuma ação é necessária e sua senha atual continuará segura.
                    </p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; word-break: break-all;">
                      Se o botão não funcionar, copie e cole o link no seu navegador:<br>
                      <a href="%s" style="color: #0284c7;">%s</a>
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(link, link, link);

        return dispararEmail(destinatario, "Recuperação de Senha — Trilhara", html);
    }

    private CompletableFuture<Boolean> dispararEmail(String destinatario, String assunto, String html) {
        var payload = Map.of(
                "from", remetente,
                "to", List.of(destinatario),
                "subject", assunto,
                "html", html
        );

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            LOG.infof("event=EMAIL_ENVIADO conta=%s integracao=resend",
                                    Pseudonimo.de(destinatario));
                            return true;
                        } else {
                            // D-04/R13: status sim, corpo do provedor não (pode ecoar o destinatário).
                            LOG.errorf("event=EMAIL_FALHA conta=%s integracao=resend status=%d",
                                    Pseudonimo.de(destinatario), response.statusCode());
                            return false;
                        }
                    })
                    .exceptionally(ex -> {
                        LOG.errorf(ex, "event=EMAIL_ERRO_REDE conta=%s integracao=resend",
                                Pseudonimo.de(destinatario));
                        return false;
                    });
        } catch (Exception e) {
            LOG.errorf(e, "event=EMAIL_PAYLOAD_INVALIDO conta=%s integracao=resend",
                    Pseudonimo.de(destinatario));
            return CompletableFuture.completedFuture(false);
        }
    }

    private String normalizarBaseUrl(String baseUrl) {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : frontendUrlPadrao;
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }
}
