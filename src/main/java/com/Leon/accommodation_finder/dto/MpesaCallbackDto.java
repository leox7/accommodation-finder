package com.Leon.accommodation_finder.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

// What Safaricom sends to /api/payments/callback when an STK Push finishes, for example:
// { "Body": { "stkCallback": { "MerchantRequestID": "...", "CheckoutRequestID": "ws_CO_...",
//     "ResultCode": 0, "ResultDesc": "The service request is processed successfully.",
//     "CallbackMetadata": { "Item": [ { "Name": "Amount", "Value": 1 },
//                                     { "Name": "MpesaReceiptNumber", "Value": "NLJ7RT61SV" }, ... ] } } } }
// The items also include the payer's phone number: never log this object.
// Unknown fields are ignored so a new field from Safaricom cannot break the callback.
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpesaCallbackDto {

    @JsonProperty("Body")
    private Body body;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("stkCallback")
        private StkCallback stkCallback;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StkCallback {
        @JsonProperty("MerchantRequestID")
        private String merchantRequestId;

        @JsonProperty("CheckoutRequestID")
        private String checkoutRequestId;

        // 0 means paid, anything else means failed (for example 1032 = cancelled by the student)
        @JsonProperty("ResultCode")
        private Integer resultCode;

        @JsonProperty("ResultDesc")
        private String resultDesc;

        // only present when the payment succeeded
        @JsonProperty("CallbackMetadata")
        private CallbackMetadata callbackMetadata;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallbackMetadata {
        @JsonProperty("Item")
        private List<Item> item;
    }

    // one Name and Value pair, for example Name "MpesaReceiptNumber", Value "NLJ7RT61SV"
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("Name")
        private String name;

        // a number for some items (Amount) and text for others (MpesaReceiptNumber)
        @JsonProperty("Value")
        private Object value;
    }
}
