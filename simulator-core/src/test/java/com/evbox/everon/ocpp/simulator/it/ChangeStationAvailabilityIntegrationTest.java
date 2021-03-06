package com.evbox.everon.ocpp.simulator.it;

import com.evbox.everon.ocpp.simulator.StationSimulatorRunner;
import com.evbox.everon.ocpp.simulator.configuration.SimulatorConfiguration;
import com.evbox.everon.ocpp.simulator.mock.ExpectedCount;
import com.evbox.everon.ocpp.simulator.mock.StationSimulatorSetUp;
import com.evbox.everon.ocpp.simulator.station.Station;
import com.evbox.everon.ocpp.simulator.station.evse.Evse;
import com.evbox.everon.ocpp.simulator.station.evse.EvseStatus;
import com.evbox.everon.ocpp.v20.message.station.ChangeAvailabilityRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.evbox.everon.ocpp.simulator.mock.ExpectedCount.times;
import static com.evbox.everon.ocpp.simulator.mock.ExpectedRequests.bootNotificationRequest;
import static com.evbox.everon.ocpp.simulator.mock.ExpectedRequests.statusNotificationRequestWithStatus;
import static com.evbox.everon.ocpp.simulator.mock.ExpectedResponses.bootNotificationResponse;
import static com.evbox.everon.ocpp.simulator.mock.ExpectedResponses.statusNotificationResponse;
import static com.evbox.everon.ocpp.simulator.support.JsonMessageTypeFactory.createCall;
import static com.evbox.everon.ocpp.simulator.support.SimulatorConfigCreator.createSimulatorConfiguration;
import static com.evbox.everon.ocpp.simulator.support.SimulatorConfigCreator.createStationConfiguration;
import static com.evbox.everon.ocpp.simulator.support.StationConstants.*;
import static com.evbox.everon.ocpp.simulator.support.StationConstants.CHANGE_AVAILABILITY_ACTION;
import static com.evbox.everon.ocpp.v20.message.station.ChangeAvailabilityRequest.OperationalStatus.INOPERATIVE;
import static com.evbox.everon.ocpp.v20.message.station.StatusNotificationRequest.ConnectorStatus.AVAILABLE;
import static com.evbox.everon.ocpp.v20.message.station.StatusNotificationRequest.ConnectorStatus.UNAVAILABLE;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ChangeStationAvailabilityIntegrationTest extends StationSimulatorSetUp {


    @BeforeEach
    void changeStationAvailabilitySetUp() {
        SimulatorConfiguration.StationConfiguration stationConfiguration = createStationConfiguration(STATION_ID, EVSE_COUNT_TWO, EVSE_CONNECTORS_TWO);
        SimulatorConfiguration simulatorConfiguration = createSimulatorConfiguration(stationConfiguration);

        stationSimulatorRunner = new StationSimulatorRunner(OCPP_SERVER_URL, simulatorConfiguration);
    }

    @Test
    void shouldChangeStationStatusToUnavailable() {

        // given
        ocppMockServer
                .when(bootNotificationRequest())
                .thenReturn(bootNotificationResponse());

        ocppMockServer
                .when(statusNotificationRequestWithStatus(AVAILABLE), times(4))
                .thenReturn(statusNotificationResponse());

        ocppMockServer
                .when(statusNotificationRequestWithStatus(UNAVAILABLE), times(4))
                .thenReturn(statusNotificationResponse());

        // when
        stationSimulatorRunner.run();

        ocppMockServer.waitUntilConnected();

        ocppServerClient.findStationSender(STATION_ID).sendMessage(changeAvailabilityRequestWithStatus(INOPERATIVE));

        // then
        await().untilAsserted(() -> {
            Station station = stationSimulatorRunner.getStation(STATION_ID);

            List<EvseStatus> evseStatuses = station.getState().getEvses().stream().map(Evse::getEvseStatus).collect(toList());

            assertThat(evseStatuses).containsOnly(EvseStatus.UNAVAILABLE);

            ocppMockServer.verify();
        });
    }

    String changeAvailabilityRequestWithStatus(ChangeAvailabilityRequest.OperationalStatus operationalStatus) {
        ChangeAvailabilityRequest changeAvailabilityRequest = new ChangeAvailabilityRequest()
                .withEvseId(EVSE_ID_ZERO)
                .withOperationalStatus(operationalStatus);

        return createCall()
                .withMessageId(DEFAULT_MESSAGE_ID)
                .withAction(CHANGE_AVAILABILITY_ACTION)
                .withPayload(changeAvailabilityRequest)
                .toJson();
    }
}
