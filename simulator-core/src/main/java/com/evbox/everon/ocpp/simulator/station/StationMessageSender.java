package com.evbox.everon.ocpp.simulator.station;

import com.evbox.everon.ocpp.simulator.message.ActionType;
import com.evbox.everon.ocpp.simulator.message.Call;
import com.evbox.everon.ocpp.simulator.message.CallResult;
import com.evbox.everon.ocpp.simulator.station.evse.Connector;
import com.evbox.everon.ocpp.simulator.station.evse.Evse;
import com.evbox.everon.ocpp.simulator.station.subscription.Subscriber;
import com.evbox.everon.ocpp.simulator.station.subscription.SubscriptionRegistry;
import com.evbox.everon.ocpp.simulator.station.support.CallIdGenerator;
import com.evbox.everon.ocpp.simulator.station.support.LRUCache;
import com.evbox.everon.ocpp.simulator.websocket.WebSocketClient;
import com.evbox.everon.ocpp.simulator.websocket.WebSocketClientInboxMessage;
import com.evbox.everon.ocpp.v20.message.station.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Send station messages to the OCPP server.
 * <p>
 * The API of this class might be changed in the future.
 */
@Slf4j
public class StationMessageSender {

    /**
     * Max number of entries in LRU cache.
     */
    private static final int MAX_CALLS = 1_000;

    private final StationState stationState;
    private final SubscriptionRegistry callRegistry;
    private final WebSocketClient webSocketClient;

    private final PayloadFactory payloadFactory = new PayloadFactory();

    private final Map<String, Call> sentCallsCache = new LRUCache<>(MAX_CALLS);

    private volatile LocalDateTime timeOfLastMessageSent;

    private final CallIdGenerator callIdGenerator = new CallIdGenerator();

    public StationMessageSender(SubscriptionRegistry subscriptionRegistry, StationState stationState, WebSocketClient webSocketClient) {
        this.stationState = stationState;
        this.callRegistry = subscriptionRegistry;
        this.webSocketClient = webSocketClient;
        this.timeOfLastMessageSent = LocalDateTime.MIN;
    }

    /**
     * Send TransactionEventStart event.
     *
     * @param evseId  evse identity
     * @param reason  reason why it was triggered
     * @param tokenId token identity
     */
    public void sendTransactionEventStart(Integer evseId, TransactionEventRequest.TriggerReason reason, String tokenId) {
        sendTransactionEventStart(evseId, null, reason, tokenId, null);
    }

    /**
     * Send TransactionEventStart event.
     *
     * @param evseId        evse identity
     * @param connectorId   connector identity
     * @param reason        reason why it was triggered
     * @param chargingState charging state of the station
     */
    public void sendTransactionEventStart(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, TransactionData.ChargingState chargingState) {
        sendTransactionEventStart(evseId, connectorId, reason, null, chargingState);
    }

    /**
     * Send TransactionEventUpdate event and subscribe on response.
     *
     * @param evseId        evse identity
     * @param connectorId   connector identity
     * @param reason        reason why it was triggered
     * @param tokenId       token identity
     * @param chargingState charging state of the station
     * @param subscriber    callback that will be executed after receiving a response from OCPP server
     */
    public void sendTransactionEventUpdateAndSubscribe(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, String tokenId, TransactionData.ChargingState chargingState,
                                                       Subscriber<TransactionEventRequest, TransactionEventResponse> subscriber) {

        TransactionEventRequest transactionEvent = payloadFactory.createTransactionEventUpdate(stationState.findEvse(evseId),
                connectorId, reason, tokenId, chargingState, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.TRANSACTION_EVENT, transactionEvent);
        callRegistry.addSubscription(call.getMessageId(), transactionEvent, subscriber);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send TransactionEventUpdate event.
     *
     * @param evseId        evse identity
     * @param connectorId   connector identity
     * @param reason        reason why it was triggered
     * @param chargingState charging state of the station
     */
    public void sendTransactionEventUpdate(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, TransactionData.ChargingState chargingState) {
        sendTransactionEventUpdate(evseId, connectorId, reason, null, chargingState);
    }

    /**
     * Send TransactionEventUpdate event.
     *
     * @param evseId        evse identity
     * @param connectorId   connector identity
     * @param reason        reason why it was triggered
     * @param tokenId       token identity
     * @param chargingState charging state of the station
     */
    public void sendTransactionEventUpdate(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, String tokenId, TransactionData.ChargingState chargingState) {

        TransactionEventRequest transactionEvent = payloadFactory.createTransactionEventUpdate(stationState.findEvse(evseId),
                connectorId, reason, tokenId, chargingState, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.TRANSACTION_EVENT, transactionEvent);


        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send TransactionEventEnded event and subscribe on response.
     *
     * @param evseId        evse identity
     * @param connectorId   connector identity
     * @param reason        reason why it was triggered
     * @param stoppedReason reason why transaction was stopped
     * @param subscriber    callback that will be executed after receiving a response from OCPP server
     */
    public void sendTransactionEventEndedAndSubscribe(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, TransactionData.StoppedReason stoppedReason,
                                                      Subscriber<TransactionEventRequest, TransactionEventResponse> subscriber) {
        TransactionEventRequest payload = payloadFactory.createTransactionEventEnded(stationState.findEvse(evseId),
                connectorId, reason, stoppedReason, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.TRANSACTION_EVENT, payload);
        callRegistry.addSubscription(call.getMessageId(), payload, subscriber);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send TransactionEventEnded event.
     *
     * @param evseId        evse identity
     * @param connectorId   connector identity
     * @param reason        reason why it was triggered
     * @param stoppedReason reason why transaction was stopped
     */
    public void sendTransactionEventEnded(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, TransactionData.StoppedReason stoppedReason) {
        TransactionEventRequest payload = payloadFactory.createTransactionEventEnded(stationState.findEvse(evseId),
                connectorId, reason, stoppedReason, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.TRANSACTION_EVENT, payload);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send Authorize event and subscribe on response.
     *
     * @param tokenId    token identity
     * @param evseIds    evse identity
     * @param subscriber callback that will be executed after receiving a response from OCPP server
     */
    public void sendAuthorizeAndSubscribe(String tokenId, List<Integer> evseIds, Subscriber<AuthorizeRequest, AuthorizeResponse> subscriber) {
        AuthorizeRequest payload = payloadFactory.createAuthorizeRequest(tokenId, evseIds);

        Call call = createAndRegisterCall(ActionType.AUTHORIZE, payload);
        callRegistry.addSubscription(call.getMessageId(), payload, subscriber);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send BootNotification event and subscribe on response.
     *
     * @param reason     reason why it was triggered
     * @param subscriber callback that will be executed after receiving a response from OCPP server
     */
    public void sendBootNotificationAndSubscribe(BootNotificationRequest.Reason reason, Subscriber<BootNotificationRequest, BootNotificationResponse> subscriber) {
        BootNotificationRequest payload = payloadFactory.createBootNotification(reason);

        Call call = createAndRegisterCall(ActionType.BOOT_NOTIFICATION, payload);
        callRegistry.addSubscription(call.getMessageId(), payload, subscriber);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send BootNotification event.
     *
     * @param reason reason why it was triggered
     */
    public void sendBootNotification(BootNotificationRequest.Reason reason) {
        BootNotificationRequest payload = payloadFactory.createBootNotification(reason);

        Call call = createAndRegisterCall(ActionType.BOOT_NOTIFICATION, payload);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send StatusNotification event and subscribe on response.
     *
     * @param evse       {@link Evse}
     * @param connector  {@link Connector}
     * @param subscriber callback that will be executed after receiving a response from OCPP server
     */
    public void sendStatusNotificationAndSubscribe(Evse evse, Connector connector, Subscriber<StatusNotificationRequest, StatusNotificationResponse> subscriber) {
        StatusNotificationRequest payload = payloadFactory.createStatusNotification(evse, connector, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.STATUS_NOTIFICATION, payload);
        callRegistry.addSubscription(call.getMessageId(), payload, subscriber);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));

    }

    /**
     * Send StatusNotification event.
     *
     * @param evseId      evse identity
     * @param connectorId connector identity
     */
    public void sendStatusNotification(int evseId, int connectorId) {
        StatusNotificationRequest payload = payloadFactory.createStatusNotification(evseId, connectorId,
                stationState.findEvse(evseId).findConnector(connectorId).getCableStatus(), stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.STATUS_NOTIFICATION, payload);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));

    }

    /**
     * Send StatusNotification event.
     *
     * @param evse      {@link Evse}
     * @param connector {@link Connector}
     */
    public void sendStatusNotification(Evse evse, Connector connector) {
        StatusNotificationRequest payload = payloadFactory.createStatusNotification(evse, connector, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.STATUS_NOTIFICATION, payload);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send HeartBeat event and subscribe on response.
     *
     * @param heartbeatRequest heart-beat request
     * @param subscriber       callback that will be executed after receiving a response from OCPP server
     */
    public void sendHeartBeatAndSubscribe(HeartbeatRequest heartbeatRequest, Subscriber<HeartbeatRequest, HeartbeatResponse> subscriber) {
        Call call = createAndRegisterCall(ActionType.HEARTBEAT, heartbeatRequest);
        callRegistry.addSubscription(call.getMessageId(), heartbeatRequest, subscriber);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    /**
     * Send an incoming message {@link WebSocketClientInboxMessage} to ocpp server.
     *
     * @param message {@link WebSocketClientInboxMessage}
     */
    public void sendMessage(WebSocketClientInboxMessage message) {
        try {
            webSocketClient.getInbox().put(message);
            timeOfLastMessageSent = LocalDateTime.now();
        } catch (InterruptedException e) {
            log.error("Exception on adding message to WebSocketInbox", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Send {@link CallResult} to ocpp server.
     *
     * @param callId  identity of the message
     * @param payload body of the message
     */
    public void sendCallResult(String callId, Object payload) {
        CallResult callResult = new CallResult(callId, payload);
        String callStr = callResult.toJson();
        sendMessage(new WebSocketClientInboxMessage.OcppMessage(callStr));
    }

    /**
     * Return unmodifiable map of registered {@link Call} calls.
     *
     * @return unmodifiable map of [callId, {@link Call}]
     */
    public Map<String, Call> getSentCalls() {
        return Collections.unmodifiableMap(sentCallsCache);
    }

    /**
     * Return the timestamp in milliseconds of the last message sent to the server.
     *
     * @return timestamp in milliseconds
     */
    public LocalDateTime getTimeOfLastMessageSent() { return timeOfLastMessageSent; }

    private void sendTransactionEventStart(Integer evseId, Integer connectorId, TransactionEventRequest.TriggerReason reason, String tokenId, TransactionData.ChargingState chargingState) {
        TransactionEventRequest transactionEvent = payloadFactory.createTransactionEventStart(stationState.findEvse(evseId),
                connectorId, reason, tokenId, chargingState, stationState.getCurrentTime());

        Call call = createAndRegisterCall(ActionType.TRANSACTION_EVENT, transactionEvent);

        sendMessage(new WebSocketClientInboxMessage.OcppMessage(call.toJson()));
    }

    private <REQ> Call createAndRegisterCall(ActionType actionType, REQ payload) {

        String callId = callIdGenerator.generate();

        Call call = new Call(callId, actionType, payload);

        sentCallsCache.put(callId, call);
        return call;
    }

}
