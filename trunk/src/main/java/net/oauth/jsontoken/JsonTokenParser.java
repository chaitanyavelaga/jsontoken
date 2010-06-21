/**
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package net.oauth.jsontoken;

import java.security.SignatureException;
import java.util.regex.Pattern;

import net.oauth.jsontoken.crypto.AsciiStringVerifier;
import net.oauth.jsontoken.crypto.Verifier;
import net.oauth.jsontoken.discovery.VerifierProviders;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Class that parses and verifies JSON Tokens.
 */
public class JsonTokenParser {

  private final Clock clock;
  private final VerifierProviders locators;
  private final AudienceChecker audienceChecker;

  /**
   * Creates a new {@link JsonTokenParser} with a default system clock. The default
   * system clock tolerates a clock skew of up to {@link SystemClock#DEFAULT_ACCEPTABLE_CLOCK_SKEW}.
   *
   * @param locators an object that provides signature verifiers, based signature algorithm,
   *   as well as on the signer and key ids.
   * @param checker an audience checker that validates the audience in the JSON token.
   */
  public JsonTokenParser(VerifierProviders locators, AudienceChecker checker) {
    this(new SystemClock(), locators, checker);
  }

  /**
   * Creates a new {@link JsonTokenParser}.
   *
   * @param clock a clock object that will decide whether a given token is currently
   *   valid or not.
   * @param locators an object that provides signature verifiers, based signature algorithm,
   *   as well as on the signer and key ids.
   * @param checker an audience checker that validates the audience in the JSON token.
   */
  public JsonTokenParser(Clock clock, VerifierProviders locators, AudienceChecker checker) {
    Preconditions.checkNotNull(clock);
    Preconditions.checkNotNull(locators);
    Preconditions.checkNotNull(checker);

    this.clock = clock;
    this.locators = locators;
    this.audienceChecker = checker;
  }

  /**
   * Parses, and verifies, a JSON Token.
   * @param tokenString the serialized token that is to parsed and verified.
   * @return the deserialized {@link JsonObject}, suitable for passing to the constructor
   *   of {@link JsonToken} or equivalent constructor of {@link JsonToken} subclasses.
   * @throws SignatureException if the signature doesn't check out, or if the token is otherwise invalid.
   */
  public JsonObject verifyAndDeserialize(String tokenString)
      throws SignatureException {
    String[] pieces = tokenString.split(Pattern.quote(JsonTokenUtil.DELIMITER));
    if (pieces.length != 2) {
      throw new IllegalArgumentException("token did not have two separate parts");
    }
    String payloadString = pieces[0];
    String signature = pieces[1];

    JsonObject json = new JsonParser().parse(fromBase64ToJsonString(payloadString)).getAsJsonObject();
    JsonToken payload = new JsonToken(json);

    Verifier verifier = locators.getVerifierProvider(payload.getSignatureAlgorithm())
        .findVerifier(payload.getIssuer(), payload.getKeyId());
    AsciiStringVerifier asciiVerifier = new AsciiStringVerifier(verifier);
    asciiVerifier.verifySignature(payloadString, Base64.decodeBase64(signature));

    if (!clock.isCurrentTimeInInterval(payload.getNotBefore(), payload.getTokenLifetime())) {
      throw new SignatureException("token is not yet or no longer valid. " +
          "Token start time: " + payload.getNotBefore() + ". duration: " + payload.getTokenLifetime());
    }

    audienceChecker.checkAudience(payload.getAudience());

    return json;
  }

  private static String fromBase64ToJsonString(String source) {
    return StringUtils.newStringUtf8(Base64.decodeBase64(source));
  }
}
