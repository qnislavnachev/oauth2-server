package com.clouway.oauth2;

import com.clouway.friendlyserve.Request;
import com.clouway.friendlyserve.Response;
import com.clouway.friendlyserve.RsRedirect;
import com.clouway.oauth2.authorization.Authorization;
import com.clouway.oauth2.authorization.ClientAuthorizationRepository;
import com.clouway.oauth2.client.Client;
import com.clouway.oauth2.client.ClientRepository;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

/**
 * @author Miroslav Genov (miroslav.genov@clouway.com)
 */
class ClientAuthorizationActivity implements IdentityActivity {
  private final ClientRepository clientRepository;
  private final ClientAuthorizationRepository clientAuthorizationRepository;

  ClientAuthorizationActivity(ClientRepository clientRepository, ClientAuthorizationRepository clientAuthorizationRepository) {
    this.clientRepository = clientRepository;
    this.clientAuthorizationRepository = clientAuthorizationRepository;
  }

  @Override
  public Response execute(String identityId, Request request) {
    String responseType = request.param("response_type");
    String clientId = request.param("client_id");
    String requestedUrl = request.param("redirect_uri");
    String state = request.param("state");

    Optional<Client> possibleClientResponse = clientRepository.findById(clientId);

    if (!possibleClientResponse.isPresent()) {
      return OAuthError.unauthorizedClient();
    }

    Client client = possibleClientResponse.get();


    Optional<String> possibleRedirectUrl = client.determineRedirectUrl(requestedUrl);

    if (!possibleRedirectUrl.isPresent()) {
      return OAuthError.unauthorizedClient("Client Redirect URL is not matching the configured one.");
    }

    Optional<Authorization> possibleAuthorizationResponse = clientAuthorizationRepository.authorize(client, identityId, responseType);

    // RFC-6749 - Section: 4.2.2.1
    // The authorization server redirects the user-agent by
    // sending the following HTTP response:
    // HTTP/1.1 302 Found
    // Location: https://client.example.com/cb#error=access_denied&state=xyz
    if (!possibleAuthorizationResponse.isPresent()) {
      return new RsRedirect(possibleRedirectUrl.get() + "?error=access_denied");
    }

    String callback = possibleRedirectUrl.get();

    Authorization authorization = possibleAuthorizationResponse.get();
    return new RsRedirect(createCallbackUrl(callback, authorization.code, state));
  }

  private String createCallbackUrl(String callback, String code, String state) {
    if (Strings.isNullOrEmpty(state)) {
      return String.format("%s?code=%s", callback, code);
    }
    return String.format("%s?code=%s&state=%s", callback, code, state);
  }
}
