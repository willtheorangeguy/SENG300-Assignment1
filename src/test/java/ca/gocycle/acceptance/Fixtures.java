package ca.gocycle.acceptance;

import ca.gocycle.application.*;
import ca.gocycle.config.*;
import ca.gocycle.data.*;
import ca.gocycle.domain.*;
import ca.gocycle.domain.vehicle.*;
import ca.gocycle.domain.rider.*;
import ca.gocycle.domain.station.*;
import ca.gocycle.domain.trip.*;
import ca.gocycle.integration.*;
import ca.gocycle.pricing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Test data and controlled provider doubles; no production business behaviour. */
final class Fixtures {
    static final Instant NOW=Instant.parse("2026-10-01T12:00:00Z");
    static DeploymentConfiguration config() throws Exception { return new ObjectMapper().readValue(Path.of("config/gocycle.example.json").toFile(),DeploymentConfiguration.class); }
    static Rider rider() { return rider(null,null,null); }
    static Rider rider(String institution,Instant until,Membership membership) {
        return new Rider(UUID.randomUUID(),"Test Rider","rider@ucalgary.ca",LocalDate.of(2000,1,1),institution,until,membership,new InterfacePreferences("en",false));
    }
    static PedalBike bike() { return new PedalBike(UUID.randomUUID(),VehicleServiceStatus.IN_SERVICE,VehicleAvailability.AVAILABLE); }
    static ElectricBike electric(Integer battery,Instant observed) { return new ElectricBike(UUID.randomUUID(),VehicleServiceStatus.IN_SERVICE,VehicleAvailability.AVAILABLE,battery,observed,600); }
    static PricingConfiguration.FareTerms terms(String category) {
        return new PricingConfiguration.FareTerms(category,new BigDecimal("5.00"),new BigDecimal(category.equals("CASUAL")?"0.50":"0.20"),new BigDecimal("50.00"),new BigDecimal("15.00"),"MINUTE","UP",null);
    }
    static Trip ended(long seconds) { return trip(rider(),bike(),NOW,NOW.plusSeconds(seconds),TripStatus.ENDED,PaymentStatus.PENDING,DepositStatus.HELD,terms("CASUAL")); }
    static Trip trip(Rider r,Vehicle v,Instant start,Instant end,TripStatus status,PaymentStatus paid,DepositStatus deposit,PricingConfiguration.FareTerms terms) {
        return new Trip(UUID.randomUUID(),r,v,"origin",end==null?null:"destination-dock",start,end,status,paid,deposit,terms,"hold-1",null,PaymentMethod.CREDIT_CARD,Set.of(PaymentMethod.CREDIT_CARD),null);
    }
    static DockingPoint dock(String id,Vehicle occupant) { return new DockingPoint(id,true,EnumSet.allOf(VehicleType.class),occupant,occupant!=null); }
    static Zone zone(String id,Set<PaymentMethod> methods) { return new Zone(id,methods,new DisplayConfiguration("#176B56","#FFFFFF","assets/central-logo.svg")); }
    static Station station(String id,double lat,double lon,boolean open,List<DockingPoint> docks) { return new Station(id,id,lat,lon,zone("central",EnumSet.allOf(PaymentMethod.class)),open,docks); }
    static PricingService pricing(DeploymentConfiguration c) { return new PricingService(List.of(new StudentPricingPolicy(),new MemberPricingPolicy(),new CasualPricingPolicy()),c); }
    static final class MutableClock extends Clock {
        private Instant now=NOW;
        void advance(Duration d) { now=now.plus(d); }
        @Override public Instant instant(){return now;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
    }
    static final class Gateway implements PaymentGateway {
        boolean hold=true,charge=true,release=true;int holds,charges,releases;
        final Map<String,PaymentResult> cached=new HashMap<>();
        private PaymentResult result(String key,boolean outcome,String ref) { return cached.computeIfAbsent(key,k->new PaymentResult(outcome,outcome?ref:null,outcome?null:"DECLINED",outcome?null:"controlled failure")); }
        @Override public PaymentResult authorizeHold(BigDecimal amount,String currency,PaymentMethod method,String requestId){if(!cached.containsKey(requestId))holds++;return result(requestId,hold,"hold-1");}
        @Override public PaymentResult charge(BigDecimal amount,String currency,PaymentMethod method,String requestId){if(!cached.containsKey(requestId))charges++;return result(requestId,charge,"charge-1");}
        @Override public PaymentResult releaseHold(String reference,String requestId){if(!cached.containsKey(requestId))releases++;return result(requestId,release,"release-1");}
    }
    static final class Docking implements DockingGateway {
        boolean unlock=true,lock=true;int unlocks,locks;
        @Override public boolean requestUnlock(String dock,UUID vehicle,String request){unlocks++;return unlock;}
        @Override public boolean confirmLocked(String dock,UUID vehicle){locks++;return lock;}
    }
    static final class Delivery implements ReceiptDelivery {
        int calls;boolean succeeds=true;final List<Receipt> delivered=new ArrayList<>();
        @Override public boolean deliver(Receipt receipt,ReceiptChannel channel,Rider rider){calls++;if(succeeds)delivered.add(receipt);return succeeds;}
    }
    static final class Network {
        final DeploymentConfiguration config;
        final MutableClock clock=new MutableClock();final Gateway gateway=new Gateway();final Docking docking=new Docking();final Delivery delivery=new Delivery();
        final ReentrantLock lock=new ReentrantLock();final PedalBike vehicle=bike();
        final Station origin=station("origin",51.0,-114.0,true,List.of(dock("origin-dock",vehicle)));
        final Station destination=station("destination",51.01,-114.0,true,List.of(dock("destination-dock",null)));
        final PaymentService payments;final RiderService riders;final StationService stations;final PricingService pricing;final TripService trips;
        Network() throws Exception {
            config=config();payments=new PaymentService(gateway,delivery,config,clock);riders=new RiderService(payments,config,clock);
            stations=new StationService(Map.of("origin",origin,"destination",destination),Map.of(vehicle.getId(),vehicle),config,clock,lock);
            pricing=pricing(config);trips=new TripService(riders,stations,pricing,payments,docking,config,clock,lock);
        }
        Rider registered(){return riders.register("Test Rider","rider@ucalgary.ca",LocalDate.of(2000,1,1));}
        Trip start(Rider r){return trips.startTrip(r.getId(),vehicle.getId(),PaymentMethod.CREDIT_CARD);}
    }
}
