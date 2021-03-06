package com.clouway.oauth2;

import com.clouway.friendlyserve.FkRegex;
import com.clouway.friendlyserve.RequestHandlerMatchingParam;
import com.clouway.friendlyserve.RequiresHeader;
import com.clouway.friendlyserve.RequiresParam;
import com.clouway.friendlyserve.TkFork;
import com.clouway.friendlyserve.servlets.ServletApiSupport;
import com.clouway.oauth2.jws.RsaJwsSignature;
import com.clouway.oauth2.jws.Signature;
import com.clouway.oauth2.jws.SignatureFactory;
import com.clouway.oauth2.jwt.Jwt.Header;
import com.clouway.oauth2.jwt.JwtController;
import com.google.common.base.Optional;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Miroslav Genov (miroslav.genov@clouway.com)
 */
public abstract class OAuth2Servlet extends HttpServlet {
  private ServletApiSupport servletApiSupport;

  @Override
  public void init(ServletConfig servletConfig) throws ServletException {
    super.init(servletConfig);

    final SignatureFactory signatureFactory = new SignatureFactory() {
      @Override
      public Optional<Signature> createSignature(byte[] signatureValue, Header header) {
        // It's vulnerable multiple algorithms to be supported, so server need to reject
        // any calls from not supported algorithms
        // More infromation could be taken from: https://auth0.com/blog/2015/03/31/critical-vulnerabilities-in-json-web-token-libraries/
        if (!"RS256".equals(header.alg)) {
          return Optional.absent();
        }
        return Optional.<Signature>of(new RsaJwsSignature(signatureValue));
      }
    };

    OAuth2Config config = config();

    TkFork fork = new TkFork(
            new FkRegex(".*/auth",
                    new InstantaneousRequestController(
                            new IdentityController(
                                    config.resourceOwnerIdentityFinder(),
                                    new ClientAuthorizationActivity(config.clientRepository(), config.clientAuthorizationRepository()), config.loginPageUrl())
                    )
            ),
            new FkRegex(".*/token",
                    new TkFork(
                            new RequestHandlerMatchingParam("grant_type", "authorization_code",
                                    new RequiresHeader("Authorization",
                                            new InstantaneousRequestController(
                                                    new BasicAuthenticationCredentialsRequest(
                                                            new ClientController(
                                                                    config.clientRepository(),
                                                                    new IssueNewTokenActivity(
                                                                            config.tokens(), config.clientAuthorizationRepository()
                                                                    )
                                                            )
                                                    ))
                                    )),
                            new RequestHandlerMatchingParam("grant_type", "refresh_token",
                                    new RequiresHeader("Authorization",
                                            new InstantaneousRequestController(
                                                    new BasicAuthenticationCredentialsRequest(
                                                            new ClientController(
                                                                    config.clientRepository(),
                                                                    new RefreshTokenActivity(config.tokens())
                                                            ))

                                            )
                                    )),
                            // JWT Support
                            new RequestHandlerMatchingParam("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer", new RequiresParam("assertion",
                                    new InstantaneousRequestController(
                                            new JwtController(
                                                    signatureFactory,
                                                    config.tokens(),
                                                    config.serviceAccountRepository()
                                            )))
                            ))
            ),
            new FkRegex(".*/revoke",
                    new RequiresParam("token",
                            new InstantaneousRequestController(
                                    new BasicAuthenticationCredentialsRequest(
                                            new RevokeTokenController(config.clientRepository(), config.tokens())
                                    )
                            )
                    )
            ),
            new FkRegex(".*/tokenInfo",
                    new RequiresParam("access_token",
                            new InstantaneousRequestController(
                                    new TokenInfoController(config.tokens())
                            )
                    )
            ),
            new FkRegex(".*/userInfo",
                    new RequiresParam("access_token",
                            new InstantaneousRequestController(
                                    new UserInfoController(config.identityFinder(), config.tokens())
                            ))
            )
    );

    servletApiSupport = new ServletApiSupport(fork);
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    super.service(req, resp);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
    servletApiSupport.serve(req, resp);
  }

  protected abstract OAuth2Config config();

}
