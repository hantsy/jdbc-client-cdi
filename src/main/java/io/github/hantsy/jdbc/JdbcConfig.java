package io.github.hantsy.jdbc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class JdbcConfig {

    @Inject
    @ConfigProperty(name = "jdbcclient.placeholder.symbol", defaultValue = "?")
    private String positionalPlaceholderSymbol;

    @Inject
    @ConfigProperty(name = "jdbcclient.metrics.enabled", defaultValue = "true")
    private boolean metricsEnabled;

    // Zero-arg constructor for CDI proxying compliance
    public JdbcConfig() {}

    public String getPositionalPlaceholderSymbol() {
        return positionalPlaceholderSymbol;
    }

    public void setPositionalPlaceholderSymbol(String symbol) {
        this.positionalPlaceholderSymbol = symbol;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }
}
