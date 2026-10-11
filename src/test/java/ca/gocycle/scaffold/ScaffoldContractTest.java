package ca.gocycle.scaffold;

import ca.gocycle.application.*;
import ca.gocycle.config.DeploymentConfiguration;
import ca.gocycle.data.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.fault.*;
import ca.gocycle.integration.*;
import ca.gocycle.presentation.*;
import ca.gocycle.pricing.*;
import ca.gocycle.simulation.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ScaffoldContractTest {
    @TestFactory
    Stream<DynamicTest> sourceTypesLoad() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        List<Path> sources;
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertFalse(sources.isEmpty(), "No Java source files found");
        return sources.stream().map(path -> {
            String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
            String type = relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
            return DynamicTest.dynamicTest(type, () -> assertNotNull(Class.forName(type)));
        });
    }

    @Test
    void vehicleHierarchySeparatesBatteryVehicles() {
        assertEquals(Vehicle.class, PedalBike.class.getSuperclass());
        assertEquals(Vehicle.class, ElectricVehicle.class.getSuperclass());
        assertEquals(ElectricVehicle.class, ElectricBike.class.getSuperclass());
        assertEquals(ElectricVehicle.class, ElectricScooter.class.getSuperclass());
        assertTrue(Modifier.isAbstract(Vehicle.class.getModifiers()));
        assertTrue(Modifier.isAbstract(ElectricVehicle.class.getModifiers()));
    }

    @Test
    void strategiesObserversAndSimulatorsImplementTheirInterfaces() {
        for (Class<?> policy : List.of(CasualPricingPolicy.class, MemberPricingPolicy.class, StudentPricingPolicy.class)) {
            assertTrue(PricingPolicy.class.isAssignableFrom(policy), policy.getSimpleName());
        }
        assertTrue(FaultObserver.class.isAssignableFrom(MaintenanceNotifier.class));
        assertTrue(FaultObserver.class.isAssignableFrom(OperatorDashboard.class));
        assertTrue(PaymentGateway.class.isAssignableFrom(SimulatedPaymentGateway.class));
        assertTrue(DockingGateway.class.isAssignableFrom(SimulatedDockingGateway.class));
        assertTrue(ReceiptDelivery.class.isAssignableFrom(SimulatedReceiptDelivery.class));
    }

    @Test
    void constructorsAcceptIntegrationAbstractionsAndSharedLock() throws Exception {
        assertNotNull(PaymentService.class.getConstructor(
                PaymentGateway.class, ReceiptDelivery.class, DeploymentConfiguration.class, Clock.class));
        assertNotNull(TripService.class.getConstructor(
                RiderService.class, StationService.class, PricingService.class, PaymentService.class,
                DockingGateway.class, DeploymentConfiguration.class, Clock.class, ReentrantLock.class));
        assertNotNull(StationService.class.getConstructor(
                Map.class, Map.class, DeploymentConfiguration.class, Clock.class, ReentrantLock.class));
    }

    @Test
    void dataResultsAreRecords() {
        for (Class<?> type : List.of(FareBreakdown.class, PaymentResult.class, Receipt.class,
                DisplayConfiguration.class, InterfacePreferences.class)) {
            assertTrue(type.isRecord(), type.getSimpleName());
        }
    }

    @Test
    void entryPointAndReturnContractAreAvailable() throws Exception {
        var main = ConsoleApplication.class.getMethod("main", String[].class);
        assertTrue(Modifier.isStatic(main.getModifiers()));
        assertEquals(void.class, main.getReturnType());
        assertEquals(Optional.class,
                TripService.class.getMethod("returnVehicle", UUID.class, String.class).getReturnType());
    }
}
