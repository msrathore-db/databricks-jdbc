package com.databricks.jdbc.auth;

import com.databricks.sdk.core.DatabricksConfig;
import com.databricks.sdk.core.oauth.ExternalBrowserCredentialsProvider;
import com.databricks.sdk.core.oauth.TokenCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Variant of the SDK's ExternalBrowserCredentialsProvider that does NOT inject {@code
 * {azureResourceId}/user_impersonation} into the scope list. The SDK adds that scope
 * unconditionally for any {@code *.azuredatabricks.net} host, which breaks the browser flow when
 * the authorize endpoint is Databricks-native (/oidc/v1/authorize) — that endpoint rejects the
 * Azure resource scope with invalid_scope.
 */
public class NonAzureBrowserCredentialsProvider extends ExternalBrowserCredentialsProvider {
  public NonAzureBrowserCredentialsProvider(TokenCache tokenCache) {
    super(tokenCache);
  }

  @Override
  protected List<String> getScopes(DatabricksConfig config) {
    Set<String> scopes = new HashSet<>(config.getScopes());
    if (!config.getDisableOauthRefreshToken()) {
      scopes.add("offline_access");
    }
    return new ArrayList<>(scopes);
  }
}
