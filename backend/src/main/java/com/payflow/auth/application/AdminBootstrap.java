package com.payflow.auth.application;

import com.payflow.auth.domain.Role;
import com.payflow.auth.domain.User;
import com.payflow.auth.domain.UserRepository;
import com.payflow.shared.validation.CpfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Cria o administrador inicial a partir de variáveis de ambiente, apenas se ele ainda não existir.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String document;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.document:}") String document) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.document = document;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        if (!CpfValidator.isValid(document)) {
            log.warn("ADMIN_DOCUMENT inválido; administrador inicial não foi criado");
            return;
        }
        users.save(new User("Administrador", normalized, passwordEncoder.encode(password),
                CpfValidator.normalize(document), Role.ADMIN));
        log.info("Administrador inicial criado: {}", normalized);
    }
}
