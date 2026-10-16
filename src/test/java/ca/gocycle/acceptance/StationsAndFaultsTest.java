package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.fault.*;
import ca.gocycle.fault.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static ca.gocycle.acceptance.Fixtures.*;

class StationsAndFaultsTest {
    @Test void nearestStationUsesIdTieAndExcludesClosedOrUnusable() throws Exception {
        var source=station("source",51,-114,true,List.of(dock("source-dock",bike())));
        var a=station("a",51.01,-114,true,List.of(dock("a-dock",null)));
        var b=station("b",51.01,-114,true,List.of(dock("b-dock",null)));
        var closed=station("closed",51.0001,-114,false,List.of(dock("closed-dock",null)));
        var unusable=station("unusable",51.0001,-114,true,List.of(new DockingPoint("bad-dock",false,EnumSet.allOf(VehicleType.class),null,false)));
        var service=new StationService(Map.of("source",source,"a",a,"b",b,"closed",closed,"unusable",unusable),Map.of(),config(),new MutableClock(),new ReentrantLock());
        assertEquals("a",service.nearestAvailable("source",VehicleType.PEDAL_BIKE).orElseThrow().getId());
    }
    @Test void relocationFailurePreservesSourceOccupancy() throws Exception {
        var n=new Network();n.destination.getDockingPoints().getFirst().attach(bike(),true);
        assertThrows(IllegalStateException.class,()->n.stations.relocate(n.vehicle.getId(),"destination"));
        assertEquals(n.vehicle,n.origin.getDockingPoints().getFirst().getVehicle());
        assertEquals("origin-dock",n.stations.findVehicleDock(n.vehicle.getId()).orElseThrow().getId());
    }
    @Test @Tag("pending-implementation") void rentedVehicleCannotRelocate() throws Exception {
        var n=new Network();n.start(n.registered());
        assertThrows(IllegalStateException.class,()->n.stations.relocate(n.vehicle.getId(),"destination"));assertEquals(VehicleAvailability.RENTED,n.vehicle.getAvailability());
    }
    @Test void faultBlocksRentalAndNotifiesBothObservers() throws Exception {
        var n=new Network();var first=new ArrayList<FaultReport>();var second=new ArrayList<FaultReport>();
        var service=new FaultService(List.of(first::add,second::add),n.stations,n.config,n.clock);
        var report=service.reportProblem(n.vehicle.getId(),FaultType.BROKEN_BRAKES,"brakes failed");
        assertEquals(VehicleServiceStatus.OUT_OF_SERVICE,n.vehicle.getServiceStatus());assertFalse(n.vehicle.isRentable(NOW));
        assertEquals(List.of(report),first);assertEquals(List.of(report),second);
    }
    @Test void failingObserverDoesNotStopAnotherOrUndoStatus() throws Exception {
        var n=new Network();var received=new ArrayList<FaultReport>();
        FaultObserver fails=report->{throw new IllegalStateException("controlled observer failure");};
        var service=new FaultService(List.of(fails,received::add),n.stations,n.config,n.clock);
        var report=assertDoesNotThrow(()->service.reportProblem(n.vehicle.getId(),FaultType.FLAT_TIRE,"flat"));
        assertEquals(List.of(report),received);assertEquals(VehicleServiceStatus.OUT_OF_SERVICE,n.vehicle.getServiceStatus());
    }
    @Test @Tag("pending-implementation") void faultOnActiveTripPreservesTripAndBlocksFutureRentals() throws Exception {
        var n=new Network();var t=n.start(n.registered());var service=new FaultService(List.of(),n.stations,n.config,n.clock);
        service.reportProblem(n.vehicle.getId(),FaultType.BROKEN_BRAKES,"bad brakes");
        assertEquals(TripStatus.ACTIVE,t.getStatus());assertNull(t.getEndedAt());assertEquals(VehicleServiceStatus.OUT_OF_SERVICE,n.vehicle.getServiceStatus());
        assertFalse(n.vehicle.isRentable(NOW));
    }
    @Test void duplicateFaultIsDiscarded() throws Exception {
        var n=new Network();var received=new ArrayList<FaultReport>();var service=new FaultService(List.of(received::add),n.stations,n.config,n.clock);
        var first=service.reportProblem(n.vehicle.getId(),FaultType.FLAT_TIRE,"flat");
        var duplicate=service.reportProblem(n.vehicle.getId(),FaultType.FLAT_TIRE,"flat");
        assertEquals(first.getId(),duplicate.getId());assertEquals(1,received.size());
    }
}
