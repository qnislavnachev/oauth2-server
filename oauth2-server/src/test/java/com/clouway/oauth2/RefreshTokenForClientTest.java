package com.clouway.oauth2;

import com.clouway.friendlyserve.Response;
import com.clouway.friendlyserve.testing.ParamRequest;
import com.clouway.friendlyserve.testing.RsPrint;
import com.clouway.oauth2.client.Client;
import com.clouway.oauth2.token.TokenResponse;
import com.clouway.oauth2.token.Tokens;
import com.google.common.collect.ImmutableMap;
import org.jmock.Expectations;
import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static com.clouway.oauth2.TokenBuilder.aNewToken;
import static com.clouway.oauth2.client.ClientBuilder.aNewClient;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * @author Miroslav Genov (miroslav.genov@clouway.com)
 */
public class RefreshTokenForClientTest {

  @Rule
  public JUnitRuleMockery context = new JUnitRuleMockery();

  @Mock
  Tokens tokens;

  @Test
  public void happyPath() throws IOException {
    RefreshTokenActivity action = new RefreshTokenActivity(tokens);
    Client client = aNewClient().withId("client1").withSecret("secret1").build();
    final DateTime anyTime = new DateTime();

    context.checking(new Expectations() {{
      oneOf(tokens).refreshToken("::refresh_token::", anyTime);
      will(returnValue(
              new TokenResponse(true, aNewToken().withValue("::access_token::").expiresAt(anyTime.plusSeconds(600)).build(), "::refresh_token::")
      ));
    }});

    Response response = action.execute(client, new ParamRequest(ImmutableMap.of("refresh_token", "::refresh_token::")), anyTime);

    String body = new RsPrint(response).printBody();

    assertThat(body, containsString("::access_token::"));
    assertThat(body, containsString("600"));
    assertThat(body, containsString("::refresh_token::"));
  }

  @Test
  public void refreshTokenWasExpired() throws IOException {
    RefreshTokenActivity action = new RefreshTokenActivity(tokens);
    Client client = aNewClient().withId("client1").withSecret("secret1").build();
    final DateTime anyTime = new DateTime();

    context.checking(new Expectations() {{
      oneOf(tokens).refreshToken("::refresh_token::", anyTime);
      will(returnValue(new TokenResponse(false, null, "")));
    }});

    Response response = action.execute(client, new ParamRequest(ImmutableMap.of("refresh_token", "::refresh_token::")), anyTime);

    String body = new RsPrint(response).printBody();

    assertThat(body, containsString("invalid_grant"));
  }

}