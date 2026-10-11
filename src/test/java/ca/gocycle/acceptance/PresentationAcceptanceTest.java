package ca.gocycle.acceptance;

import ca.gocycle.data.*;
import ca.gocycle.presentation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static ca.gocycle.acceptance.Fixtures.*;

class PresentationAcceptanceTest {
    @Test @Tag("pending-implementation") void preferencesPersistSupportedLanguageAndAudio() throws Exception {
        var n=new Network();var r=n.registered();n.riders.updatePreferences(r.getId(),new InterfacePreferences("fr",true));
        assertEquals(new InterfacePreferences("fr",true),n.riders.findRider(r.getId()).orElseThrow().getPreferences());
        assertThrows(IllegalArgumentException.class,()->n.riders.updatePreferences(r.getId(),new InterfacePreferences("unsupported",false)));
    }
    @ParameterizedTest @Tag("pending-implementation") @CsvSource({"en,Choose","fr,Choisissez"})
    void helpUsesConfiguredLanguageBrandingAndSimulatedAudio(String language,String instruction) throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var tree=json.valueToTree(config());
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("interfaces")).put("defaultLanguage",language);
        var c=json.treeToValue(tree,ca.gocycle.config.DeploymentConfiguration.class);var n=new Network();
        var app=new ConsoleApplication(c,n.riders,n.trips,new ca.gocycle.application.FaultService(java.util.List.of(),n.stations,c,n.clock),n.stations,new OperatorDashboard(n.stations));
        var oldIn=System.in;var oldOut=System.out;var output=new ByteArrayOutputStream();
        try {
            // Proposed console acceptance protocol: help, audio on, help, quit.
            System.setIn(new ByteArrayInputStream("help\naudio on\nhelp\nquit\n".getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(output,true,StandardCharsets.UTF_8));app.run();
        } finally {System.setIn(oldIn);System.setOut(oldOut);}
        var rendered=output.toString(StandardCharsets.UTF_8);assertTrue(rendered.contains(instruction));
        assertTrue(rendered.contains("#176B56"));assertTrue(rendered.contains("central-logo.svg"));assertTrue(rendered.contains("[audio]"));
    }
    @Test @Tag("pending-implementation") void explicitConfigPathRejectsMissingFileBeforeStartup() {
        var error=assertThrows(IllegalArgumentException.class,()->ConsoleApplication.main(new String[]{"--config","config/missing-explicit.json"}));
        assertTrue(error.getMessage().contains("missing-explicit.json"));
    }
}
