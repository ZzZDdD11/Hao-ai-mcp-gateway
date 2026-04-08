package com.hao.ai.domain.session.model.valobj;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class McpSchemaVO {


    public sealed interface JSONRPCMessage permits JSONRPCRequest, JSONRPCResponse, JSONRPCNotification{
        String jsonrpc();
    }


    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JSONRPCRequest(@JsonProperty("jsonrpc") String jsonrpc,
                                 @JsonProperty("method") String method,
                                 @JsonProperty("id") Object id,
                                 @JsonProperty("params") Object params
    ) implements JSONRPCMessage {
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JSONRPCResponse(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("id") Object id,
            @JsonProperty("result") Object result,
            @JsonProperty("error") JSONRPCError error
    ) implements JSONRPCMessage {
        @JsonInclude(JsonInclude.Include.NON_ABSENT)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record JSONRPCError(
                @JsonProperty("code") int code,
                @JsonProperty("message") String message,
                @JsonProperty("data") Object data) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JSONRPCNotification(
            @JsonProperty("jsonrpc") String jsonrpc,
            @JsonProperty("method") String method,
            @JsonProperty("params") Object params) implements JSONRPCMessage {
    }


}
