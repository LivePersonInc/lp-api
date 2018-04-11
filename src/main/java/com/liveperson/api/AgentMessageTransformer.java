package com.liveperson.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.liveperson.api.infra.ws.MessageTransformer;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;

public class AgentMessageTransformer implements MessageTransformer {
    final Map<String,String> params;
    public static final String AGENT_ID = "agentId";
    public static final String AGENT_OLD_ID = "agentOldId";
    public static final String ACCOUNT = "account";

    public AgentMessageTransformer(Map<String, String> params) {
        this.params = params;
    }

    @Override
    public Optional<String> getParam(String key) {
        return Optional.ofNullable(params.get(key));
    }

    @Override
    public List<JsonNode> outgoing(ObjectNode msg) {
        ObjectNode clone = msg.deepCopy();
        switch (clone.path("type").asText()) {
            case "routing.SetAgentState":
                clone.put("type", ".ams.routing.SetAgentState")
                        .with("body")
                        .put("agentUserId",getParam(AGENT_OLD_ID).get())
                        .withArray("channels").add("MESSAGING");
                break;
            case "routing.SubscribeRoutingTasks":
                clone.put("type", ".ams.routing.SubscribeRoutingTasks")
                        .with("body")
                        .put("channelType","MESSAGING")
                        .put("agentId",getParam(AGENT_OLD_ID).get())
                        .put("brandId",getParam(ACCOUNT).get());
                break;
            case "cqm.SubscribeExConversations":
                ArrayNode agentOldIds = clone.put("type", ".ams.aam.SubscribeExConversations")
                        .with("body")
                        .withArray("agentIds").removeAll();
                streamOf(msg.with("body").withArray("agentIds").elements())
                        .filter(id -> id.asText().equals(getParam(AGENT_ID).get()))
                        .map(id -> getParam(AGENT_OLD_ID).get())
                        .forEachOrdered(id->agentOldIds.add(id));
                break;
            case "routing.UpdateRingState":
                clone.put("type", ".ams.routing.UpdateRingState");
                break;
        }
        return asList(clone);
    }

    @Override
    public List<JsonNode> incoming(ObjectNode msg) {
        ObjectNode clone = msg.deepCopy();
        switch (clone.path("type").asText()) {
            case ".ams.ms.OnlineEventDistribution":
                clone.remove("body");
                ObjectNode newChange = ((ObjectNode) msg.path("body").deepCopy())
                        .put("originatorId", msg.path("body").path("originatorPId").asText());
                newChange.remove("originatorPId");
                clone.put("type", "ms.MessagingEventNotification")
                        .with("body")
                        .put("dialogId",msg.path("body").path("dialogId").asText())
                        .withArray("changes")
                        .add(newChange);
                break;
            case ".ams.routing.RoutingTaskNotification":
                clone.put("type", "routing.RoutingTaskNotification");
                break;
            case ".ams.aam.ExConversationChangeNotification":
                clone.put("type", "cqm.ExConversationChangeNotification");
                streamOf(clone.with("body").withArray("changes").elements()).map(o->(ObjectNode)o).forEach(change->{
                    change.with("result").remove("effectiveTTR");
                    change.with("result").remove("lastUpdateTime");
                    change.with("result").remove("numberOfunreadMessages");
                    change.with("result").remove("lastContentEventNotification");
                    ObjectNode convDetails = change.with("result").with("conversationDetails");
                    convDetails.remove("convId");
                    convDetails.remove("brandId");
                    convDetails.remove("dialogs");
                    convDetails.remove("note");
                    convDetails.remove("groupId");
                    convDetails.remove("csatRate");
                    convDetails.remove("participants");
                    streamOf(convDetails.with("participantsPId").fields()).forEachOrdered(roleParticipants->{
                        streamOf(roleParticipants.getValue().elements()).forEachOrdered(participantPid->{
                            convDetails.withArray("participants").addObject()
                                    .put("id",participantPid.asText())
                                    .put("role",roleParticipants.getKey());
                        });
                    });
                    convDetails.remove("participantsPId");                     ;
                });
                break;
        }
        return asList(clone);
    }

    private static <T> Stream<T> streamOf(Iterator<T> t) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(t, Spliterator.ORDERED),
                false);
    }

}
