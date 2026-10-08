package com.Leon.accommodation_finder.provider;

import com.Leon.accommodation_finder.exception.PaymentProviderException;
import com.Leon.accommodation_finder.model.Payment;
import com.Leon.accommodation_finder.model.PaymentProviderType;
import com.Leon.accommodation_finder.model.PaymentStatus;
import com.Leon.accommodation_finder.model.PaymentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

// Takes real (sandbox) M-Pesa payments through Safaricom's Daraja API with an STK Push:
// the student gets a "enter your M-Pesa PIN" prompt on their phone.
// Spring only creates it when payment.provider=mpesa.
//
// All credentials come from application-secrets.properties. NEVER log the access token,
// the consumer key or secret, the passkey, the password built from it, or the phone number.
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "mpesa")
public class MpesaPaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(MpesaPaymentProvider.class);

    // Daraja wants the time in Kenya, written like 20261007143005
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private final RestClient restClient;
    private final String consumerKey;
    private final String consumerSecret;
    private final String shortcode;
    private final String passkey;
    private final String callbackUrl;

    @Autowired
    public MpesaPaymentProvider(@Value("${daraja.base-url}") String baseUrl,
                                @Value("${daraja.consumer-key}") String consumerKey,
                                @Value("${daraja.consumer-secret}") String consumerSecret,
                                @Value("${daraja.shortcode}") String shortcode,
                                @Value("${daraja.passkey}") String passkey,
                                @Value("${daraja.callback-url}") String callbackUrl) {
        // give up instead of waiting forever when Safaricom is slow or unreachable
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public PaymentProviderType getType() {
        return PaymentProviderType.MPESA;
    }

    // Sends the STK Push. Returns PENDING when Safaricom ACCEPTED the request: that is not paid yet,
    // the real answer comes later through the callback (or the status checks).
    // The ids Safaricom gives back are put on the payment, PaymentService saves them.
    // Throws PaymentProviderException when Safaricom cannot be reached or does not accept the request.
    @Override
    public PaymentStatus startPayment(Payment payment) {
        Map<?, ?> response;
        try {
            String accessToken = getAccessToken();
            String timestamp = LocalDateTime.now(NAIROBI).format(TIMESTAMP_FORMAT);
            String shortText = payment.getType() == PaymentType.DEPOSIT ? "Deposit" : "Rent";

            Map<String, Object> body = new LinkedHashMap<>();
            // the business number (paybill) the money goes to, 174379 in the sandbox
            body.put("BusinessShortCode", shortcode);
            // proves the request comes from us: Base64 of shortcode + passkey + timestamp
            body.put("Password", buildPassword(timestamp));
            // the same time that was used inside the password
            body.put("Timestamp", timestamp);
            // the kind of payment: a customer paying to a paybill number
            body.put("TransactionType", "CustomerPayBillOnline");
            // whole shillings only, so a price like 8500.50 is rounded up to 8501
            body.put("Amount", payment.getAmount().setScale(0, RoundingMode.CEILING).longValueExact());
            // the phone number the money comes from (254XXXXXXXXX)
            body.put("PartyA", payment.getPhoneNumber());
            // who receives the money, the same business number as above
            body.put("PartyB", shortcode);
            // the phone that gets the PIN prompt, the same student's phone
            body.put("PhoneNumber", payment.getPhoneNumber());
            // where Safaricom sends the final answer (our /api/payments/callback through ngrok)
            body.put("CallBackURL", callbackUrl);
            // short text shown to the student on the prompt, kept under 12 characters
            body.put("AccountReference", shortText);
            // short description of the payment, kept under 12 characters
            body.put("TransactionDesc", shortText);

            response = restClient.post()
                    .uri("/mpesa/stkpush/v1/processrequest")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            // only the kind of error is logged, Daraja's error text might repeat what we sent
            logger.error("STK Push for payment {} failed: {}", payment.getId(), e.getClass().getSimpleName());
            throw new PaymentProviderException();
        }

        // "0" means "request accepted", anything else means Safaricom refused it
        String responseCode = response == null ? null : String.valueOf(response.get("ResponseCode"));
        if (!"0".equals(responseCode)) {
            logger.error("STK Push for payment {} was not accepted, ResponseCode {}", payment.getId(), responseCode);
            throw new PaymentProviderException();
        }

        payment.setMerchantRequestId(String.valueOf(response.get("MerchantRequestID")));
        payment.setCheckoutRequestId(String.valueOf(response.get("CheckoutRequestID")));
        logger.info("STK Push accepted for payment {}", payment.getId());
        return PaymentStatus.PENDING;
    }

    // Asks Safaricom what happened to an STK Push whose callback never arrived (STK Push Query).
    // Returns PAID or FAILED for a definite answer, or PENDING when there is no answer yet
    // ("still being processed", or Safaricom could not be reached). Never throws.
    // The query does not give a receipt number.
    public PaymentStatus queryStatus(Payment payment) {
        try {
            String accessToken = getAccessToken();
            String timestamp = LocalDateTime.now(NAIROBI).format(TIMESTAMP_FORMAT);

            Map<String, Object> body = new LinkedHashMap<>();
            // the same business number, password and timestamp as for the STK Push
            body.put("BusinessShortCode", shortcode);
            body.put("Password", buildPassword(timestamp));
            body.put("Timestamp", timestamp);
            // the id Safaricom gave the STK Push when it accepted it
            body.put("CheckoutRequestID", payment.getCheckoutRequestId());

            Map<?, ?> response = restClient.post()
                    .uri("/mpesa/stkpushquery/v1/query")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Object resultCode = response == null ? null : response.get("ResultCode");
            if (resultCode == null) {
                return PaymentStatus.PENDING;
            }
            // 0 means the student paid, any other code (cancelled, wrong PIN, no balance...) means failed
            return "0".equals(String.valueOf(resultCode)) ? PaymentStatus.PAID : PaymentStatus.FAILED;
        } catch (Exception e) {
            // Daraja answers "The transaction is being processed" with an HTTP error, so this is
            // the normal "no answer yet" case as well as the "not reachable" case
            logger.info("STK Push Query for payment {} gave no answer: {}", payment.getId(), e.getClass().getSimpleName());
            return PaymentStatus.PENDING;
        }
    }

    // A fresh OAuth token for every call, that is the simplest. It is valid about an hour
    // and proves to Safaricom that the request comes from our app (consumer key and secret).
    private String getAccessToken() {
        Map<?, ?> response = restClient.get()
                .uri("/oauth/v1/generate?grant_type=client_credentials")
                .headers(headers -> headers.setBasicAuth(consumerKey, consumerSecret))
                .retrieve()
                .body(Map.class);

        Object token = response == null ? null : response.get("access_token");
        if (token == null) {
            throw new IllegalStateException("Daraja gave no access token");
        }
        logger.info("Daraja access token obtained");
        return token.toString();
    }

    // Daraja's password: Base64 of shortcode + passkey + timestamp. Never log it, it contains the passkey.
    private String buildPassword(String timestamp) {
        String raw = shortcode + passkey + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
