package com.evbox.everon.ocpp.simulator.station.handlers.ocpp.support;

import com.evbox.everon.ocpp.simulator.station.StationMessageSender;
import com.evbox.everon.ocpp.simulator.station.StationState;
import com.evbox.everon.ocpp.simulator.station.evse.Connector;
import com.evbox.everon.ocpp.simulator.station.evse.Evse;
import com.evbox.everon.ocpp.simulator.station.evse.EvseStatus;
import com.evbox.everon.ocpp.v20.message.station.ChangeAvailabilityRequest;
import com.evbox.everon.ocpp.v20.message.station.ChangeAvailabilityResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.evbox.everon.ocpp.v20.message.station.ChangeAvailabilityResponse.Status.*;

@Slf4j
@AllArgsConstructor
public class AvailabilityManager {

    private final StationState stationState;
    private final StationMessageSender stationMessageSender;

    /**
     * Change EVSE status by doing the following:
     * <p>
     * 1. Send response with ACCEPTED status when EVSE status is the same as requested.
     * 2. Change EVSE status to the requested status when they do not match.
     * In addition send response with ACCEPTED status and StatusNotification request for every EVSE Connector.
     * 3. When a transaction is in progress.
     * Send response with SCHEDULED status and save scheduled status for further processing.
     * 4. Send response with REJECTED status when EVSE could not be found.
     *
     * @param callId              identity of the message
     * @param request             incoming request from the server
     * @param requestedEvseStatus requested EVSE status
     */
    public void changeEvseAvailability(String callId, ChangeAvailabilityRequest request, EvseStatus requestedEvseStatus) {

        try {
            Evse evse = stationState.findEvse(request.getEvseId());

            ChangeAvailabilityResponse.Status evseStatus = determineEvseStatus(requestedEvseStatus, evse);

            sendResponseWithStatus(callId, evseStatus);

            sendNotificationRequest(evse);

        } catch (IllegalArgumentException e) {
            log.error(e.getMessage(), e);
            sendResponseWithStatus(callId, REJECTED);
        }

    }

    /**
     *
     * Change Station status by doing the following:
     * <p>
     * 1. Send response with ACCEPTED status when all EVSE statuses are the same as requested.
     * 2. Change EVSE status to the requested status when they do not match.
     * In addition send response with ACCEPTED status and StatusNotification request for every EVSE Connector.
     * 3. When a transaction is in progress for at least one EVSE then send response with SCHEDULED status
     * and save scheduled status for further processing.
     * 4. Send response with REJECTED status when no EVSEs are present.
     *
     * @param callId              identity of the message
     * @param requestedEvseStatus requested EVSE status
     */
    public void changeStationAvailability(String callId, EvseStatus requestedEvseStatus) {

        if (stationState.getEvses().isEmpty()) {
            sendResponseWithStatus(callId, REJECTED);
            return;
        }

        ChangeAvailabilityResponse.Status stationStatus = determineStationStatus(requestedEvseStatus);

        sendResponseWithStatus(callId, stationStatus);

        stationState.getEvses().forEach(this::sendNotificationRequest);

    }


    private ChangeAvailabilityResponse.Status determineStationStatus(EvseStatus requestedEvseStatus) {

        ChangeAvailabilityResponse.Status stationStatus = null;

        for (Evse evse : stationState.getEvses()) {

            ChangeAvailabilityResponse.Status evseStatus = determineEvseStatus(requestedEvseStatus, evse);

            if (stationStatus != SCHEDULED) {
                stationStatus = evseStatus;
            }
        }

        return stationStatus;
    }

    private ChangeAvailabilityResponse.Status determineEvseStatus(EvseStatus requestedEvseStatus, Evse evse) {

        if (evse.hasStatus(requestedEvseStatus)) {
            return ACCEPTED;
        }

        if (evse.hasOngoingTransaction()) {
            log.info("Scheduling status: {} for evse {}", requestedEvseStatus, evse.getId());

            evse.setScheduledNewEvseStatus(requestedEvseStatus);

            return SCHEDULED;
        }

        evse.changeStatus(requestedEvseStatus);

        return ACCEPTED;

    }

    private void sendResponseWithStatus(String callId, ChangeAvailabilityResponse.Status status) {
        stationMessageSender.sendCallResult(callId, new ChangeAvailabilityResponse().withStatus(status));
    }

    private void sendNotificationRequest(Evse evse) {

        // for every connector send StatusNotification request
        for (Connector connector : evse.getConnectors()) {
            stationMessageSender.sendStatusNotification(evse, connector);
        }

    }

}
