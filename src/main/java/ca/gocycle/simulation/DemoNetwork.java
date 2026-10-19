package ca.gocycle.simulation;

import ca.gocycle.application.StationService;
import ca.gocycle.config.DeploymentConfiguration;
import ca.gocycle.data.DisplayConfiguration;
import ca.gocycle.domain.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.vehicle.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Sample stations and vehicles for running the console without real hardware. */
public final class DemoNetwork {
    private DemoNetwork() { }

    public static StationService create(DeploymentConfiguration configuration, Clock clock,
            ReentrantLock lock) {
        var stations = new LinkedHashMap<String, Station>();
        var vehicles = new LinkedHashMap<UUID, Vehicle>();
        var zones = List.of(
                new Zone("central", EnumSet.allOf(PaymentMethod.class),
                        new DisplayConfiguration("#176B56", "#FFFFFF", "config/assets/central-logo.svg")),
                new Zone("campus", EnumSet.of(PaymentMethod.CREDIT_CARD, PaymentMethod.TRANSIT_CARD),
                        new DisplayConfiguration("#D6001C", "#FFFFFF", "config/assets/campus-logo.svg")));

        for (int zoneIndex = 0; zoneIndex < zones.size(); zoneIndex++) {
            var zone = zones.get(zoneIndex);
            int stationCount = configuration.getStations().stationsPerZone().get(zoneIndex);
            for (int stationIndex = 1; stationIndex <= stationCount; stationIndex++) {
                String stationId = zone.getId() + "-" + stationIndex;
                var docks = new ArrayList<DockingPoint>();
                for (int dockIndex = 0; dockIndex < configuration.getStations().dockingPointsPerStation(); dockIndex++) {
                    String dockId = stationId + "-dock-" + (dockIndex + 1);
                    UUID vehicleId = UUID.nameUUIDFromBytes(dockId.getBytes(StandardCharsets.UTF_8));
                    Vehicle vehicle = switch (dockIndex) {
                        case 0 -> new PedalBike(vehicleId, VehicleServiceStatus.IN_SERVICE,
                                VehicleAvailability.AVAILABLE);
                        case 1 -> new ElectricBike(vehicleId, VehicleServiceStatus.IN_SERVICE,
                                VehicleAvailability.AVAILABLE, 100, clock.instant(), 600);
                        case 2 -> new ElectricScooter(vehicleId, VehicleServiceStatus.IN_SERVICE,
                                VehicleAvailability.AVAILABLE, 100, clock.instant(), 600);
                        default -> null;
                    };
                    if (vehicle != null) vehicles.put(vehicleId, vehicle);
                    docks.add(new DockingPoint(dockId, true, EnumSet.allOf(VehicleType.class),
                            vehicle, vehicle != null));
                }
                stations.put(stationId, new Station(stationId, stationId,
                        51.0 + zoneIndex * 0.03 + stationIndex * 0.005,
                        -114.0 - zoneIndex * 0.03, zone, true, docks));
            }
        }
        return new StationService(stations, vehicles, configuration, clock, lock);
    }
}
