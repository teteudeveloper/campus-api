package br.com.campusapi.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Falha na inicialização, antes de criar qualquer conexão, quando a configuração obrigatória do banco não foi
 * informada, com uma mensagem que nomeia as variáveis ausentes (sem exibir valores). O formato e o tamanho do
 * segredo JWT são validados em JwtConfig.
 */
@Component
public class VerificacaoConfiguracaoObrigatoria implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Map<String, String> OBRIGATORIAS = new LinkedHashMap<>();

    static {
        OBRIGATORIAS.put("spring.datasource.url", "DB_URL");
        OBRIGATORIAS.put("spring.datasource.username", "DB_USERNAME");
        OBRIGATORIAS.put("spring.datasource.password", "DB_PASSWORD");
    }

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> ausentes = new ArrayList<>();
        OBRIGATORIAS.forEach((propriedade, variavel) -> {
            String valor;
            try {
                valor = environment.getProperty(propriedade);
            } catch (IllegalArgumentException placeholderNaoResolvido) {
                valor = null;
            }
            if (valor == null || valor.isBlank()) {
                ausentes.add(variavel);
            }
        });
        boolean segredoEfemeroPermitido = environment.getProperty(
                "app.security.jwt.permitir-segredo-efemero", Boolean.class, false);
        String segredo = environment.getProperty("app.security.jwt.secret");
        if (!segredoEfemeroPermitido && (segredo == null || segredo.isBlank())) {
            ausentes.add("JWT_SECRET");
        }
        if (!ausentes.isEmpty()) {
            throw new IllegalStateException("Configuração obrigatória ausente: " + String.join(", ", ausentes)
                    + ". Defina as variáveis de ambiente (veja .env.example) ou use o perfil dev.");
        }
    }
}
