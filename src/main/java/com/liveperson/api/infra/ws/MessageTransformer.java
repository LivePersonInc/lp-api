package com.liveperson.api.infra.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

public interface MessageTransformer {
    default Optional<String> getParam(String key) { return Optional.empty(); }
    List<JsonNode> outgoing (ObjectNode msg);
    List<JsonNode> incoming (ObjectNode msg);
}

