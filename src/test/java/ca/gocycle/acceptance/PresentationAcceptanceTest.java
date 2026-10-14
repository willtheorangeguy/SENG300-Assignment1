package ca.gocycle.acceptance;

import ca.gocycle.data.*;
import ca.gocycle.application.*;
import ca.gocycle.domain.station.Station;
import ca.gocycle.presentation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static ca.gocycle.acceptance.Fixtures.*;

class PresentationAcceptanceTest {
    @Test @Tag("pending-implementation") void preferencesPersistSupportedLanguageAndAudio() throws Exception {
        var n=new Network();var r=n.registered();n.riders.updatePreferences(r.getId(),new InterfacePreferences("fr",true));
        assertEquals(new InterfacePreferences("fr",true),n.riders.findRider(r.getId()).orElseThrow().getPreferences());
        assertThrows(IllegalArgumentException.class,()->n.riders.updatePreferences(r.getId(),new InterfacePreferences("unsupported",false)));
    }
    @ParameterizedTest @CsvSource({"en,Choose","fr,Choisissez"})
    void helpUsesConfiguredLanguageBrandingAndSimulatedAudio(String language,String instruction) throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var tree=json.valueToTree(config());
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("interfaces")).put("defaultLanguage",language);
        var c=json.treeToValue(tree,ca.gocycle.config.DeploymentConfiguration.class);var n=new Network();
        var stations=new StationService(Map.of("origin",n.origin,"destination",n.destination),
                Map.of(n.vehicle.getId(),n.vehicle),c,n.clock,new ReentrantLock()) {
            @Override public List<Station> listStations() { return List.of(n.origin,n.destination); }
            @Override public List<ca.gocycle.domain.vehicle.Vehicle> listVehicles() { return List.of(n.vehicle); }
        };
        var dashboard=new OperatorDashboard(stations);
        var faults=new FaultService(List.of(dashboard),stations,c,n.clock);
        var app=new ConsoleApplication(c,n.riders,n.trips,faults,stations,dashboard);
        var oldIn=System.in;var oldOut=System.out;var output=new ByteArrayOutputStream();
        try {
            // Help, audio on, help, quit.
            System.setIn(new ByteArrayInputStream("help\naudio on\nhelp\nquit\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));app.run();
        } finally {System.setIn(oldIn);System.setOut(oldOut);}
        var rendered=output.toString(StandardCharsets.UTF_8);assertTrue(rendered.contains(instruction));
        assertTrue(rendered.contains("#176B56"));assertTrue(rendered.contains("central-logo.svg"));assertTrue(rendered.contains("[audio]"));
    }
    @Test void explicitConfigPathRejectsMissingFileBeforeStartup() {
        var error=assertThrows(IllegalArgumentException.class,()->ConsoleApplication.main(new String[]{"--config","config/missing-explicit.json"}));
        assertTrue(error.getMessage().contains("missing-explicit.json"));
    }
}
