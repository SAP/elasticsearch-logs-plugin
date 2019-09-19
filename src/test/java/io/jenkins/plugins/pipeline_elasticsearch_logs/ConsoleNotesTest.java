package io.jenkins.plugins.pipeline_elasticsearch_logs;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;


public class ConsoleNotesTest {

    @Test
    public void testWrite() throws IOException {
        // SETUP
        StringWriter writer = new StringWriter();
        Map<String, Object> map = new HashMap<String, Object>();
        Map<String, Object> annotation = new HashMap<String, Object>();
        
        annotation.put("note", "////4Jf1eMzwQiphNz6RXKJhGu5sA1Gmn+0FX+P6rWHYcVVUAAAAlx+LCAAAAAAAAP9b85aBtbiIQTGjNKU4P08vOT+vOD8nVc83PyU1x6OyILUoJzMv2y+/JJUBAhiZGBgqihhk0NSjKDWzXb3RdlLBUSYGJk8GtpzUvPSSDB8G5tKinBIGIZ+sxLJE/ZzEvHT94JKizLx0a6BxUmjGOUNodHsLgAzWEgZu/dLi1CL9xJTczDwAj6GcLcAAAAA=");
        annotation.put("position", 16);
        ArrayList<Object> annotations = new ArrayList<Object>();
        annotations.add(annotation);
        
        Map<String, Object> runId = new HashMap<String, Object>();
        runId.put("instance", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhhE25zb1f9xl20eVCGK5QNOwFbEW/8TlxBvdrbpv6a6xMyhGtZ64Dv6yh4CQpqXBDTtm9QavmOI/c3ImuUdPTCAtvKaAQjXb4Pt4wtaHjDnUkSOdR4GaWbZNtuc6gqqH+k53dYzbEs26j3OWil1nAf/6H7VXobM3OKOGlYGq6N5aGTqGjgtJB/QJ1ShGU20qlgkKLNcztqNCjvoG+LwAm+wtMCpSHN/kodqJ1QfuOgNjIf1KpQ6M8gtLe34ZfReozv/9sc6ZHB6InTJ+lTaPPf7cVfUvWNB2OE8oi9WYuwi4Ih6BvuzK545KEmVlOK3CmM05qMQhb1exmsVfJBuxOwIDAQAB");
        runId.put("build", 17);
        runId.put("project", "TestPipelineLogs");
        
        map.put("uid", "NWU2NWViMWUtODI1Ni00MTgyLTgwMD_17");
        map.put("timestampMillis", 1568206462711L);
        map.put("annotations", annotations);
        map.put("runId", runId);
        map.put("eventType", "buildMessage");
        map.put("message", "Started by user admin");
        map.put("timestamp", "2019-09-11T12:54:22.712Z");

        // EXERCISE
        ConsoleNotes.write(writer, map);
        
        // VERIFY
        assertEquals(
                "Started by user [8mha:////4Jf1eMzwQiphNz6RXKJhGu5sA1Gmn+0FX+P6rWHYcVVUAAAAlx+LCAAAAAAAAP9b85aBtbiIQTGjNKU4P08vOT+vOD8nVc83PyU1x6OyILUoJzMv2y+/JJUBAhiZGBgqihhk0NSjKDWzXb3RdlLBUSYGJk8GtpzUvPSSDB8G5tKinBIGIZ+sxLJE/ZzEvHT94JKizLx0a6BxUmjGOUNodHsLgAzWEgZu/dLi1CL9xJTczDwAj6GcLcAAAAA=[0madmin\n",
                writer.getBuffer().toString());
    }
    
}
